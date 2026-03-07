package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.screens.gamedetail.toAchievementUi
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.RA_BADGE_BASE_URL
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.nendo.argosy.util.parseTimestamp
import com.nendo.argosy.ui.screens.gamedetail.AchievementUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementDelegate @Inject constructor(
    private val achievementDao: AchievementDao,
    private val gameRepository: GameRepository,
    private val raRepository: RetroAchievementsRepository,
    private val romMRepository: RomMRepository,
    private val imageCacheManager: ImageCacheManager,
    private val achievementUpdateBus: AchievementUpdateBus
) {
    private val _achievements = MutableStateFlow<List<AchievementUi>>(emptyList())
    val achievements: StateFlow<List<AchievementUi>> = _achievements.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun loadCached(gameId: Long, hasRommId: Boolean) {
        val cached = achievementDao.getByGameId(gameId)
        if (!hasRommId && cached.isEmpty()) {
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
        val game = gameRepository.getById(gameId)
        val raId = game?.raId

        if (raId != null && raRepository.isLoggedIn()) {
            val raResults = fetchAchievementsFromRA(raId, gameId)
            if (raResults.isNotEmpty()) return raResults
        }

        if (rommId != null) {
            val rommResults = fetchAchievementsFromRomM(rommId, gameId)
            if (rommResults.isNotEmpty()) return rommResults
        }

        return emptyList()
    }

    private suspend fun fetchAchievementsFromRA(raId: Long, gameId: Long): List<AchievementUi> {
        val raData = raRepository.getGameAchievementsWithProgress(raId) ?: return emptyList()

        val entities = mutableListOf<AchievementEntity>()
        val uiModels = mutableListOf<AchievementUi>()

        raData.achievements.forEach { achievement ->
            val isUnlocked = achievement.id in raData.unlockedIds
            val isHardcore = achievement.id in raData.hardcoreUnlockedIds
            val badgeUrl = achievement.badgeName?.let { "${RA_BADGE_BASE_URL}$it.png" }
            val badgeUrlLock = achievement.badgeName?.let { "${RA_BADGE_BASE_URL}${it}_lock.png" }

            entities += AchievementEntity(
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

            uiModels += AchievementUi(
                raId = achievement.id,
                title = achievement.title,
                description = achievement.description ?: "",
                points = achievement.points,
                type = achievement.type,
                badgeUrl = if (isUnlocked) badgeUrl else (badgeUrlLock ?: badgeUrl),
                isUnlocked = isUnlocked,
                isUnlockedHardcore = isHardcore
            )
        }

        achievementDao.replaceForGame(gameId, entities)
        gameRepository.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())

        gameRepository.updateAchievementCount(gameId, raData.totalCount, raData.earnedCount)
        achievementUpdateBus.emit(
            AchievementUpdateBus.AchievementUpdate(gameId, raData.totalCount, raData.earnedCount)
        )

        val savedAchievements = achievementDao.getByGameId(gameId)
        savedAchievements.forEach { achievement ->
            if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                imageCacheManager.queueBadgeCache(achievement.id, achievement.badgeUrl, achievement.badgeUrlLock)
            }
        }

        return uiModels
    }

    private suspend fun fetchAchievementsFromRomM(rommId: Long, gameId: Long): List<AchievementUi> {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements ?: emptyList()
                if (apiAchievements.isEmpty()) return emptyList()

                romMRepository.refreshRAProgressionIfNeeded()
                val earnedAchievements = getEarnedAchievements(rom.raId)
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
                gameRepository.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())

                val earnedCount = entities.count { it.isUnlocked }
                gameRepository.updateAchievementCount(gameId, entities.size, earnedCount)
                achievementUpdateBus.emit(
                    AchievementUpdateBus.AchievementUpdate(gameId, entities.size, earnedCount)
                )

                val savedAchievements = achievementDao.getByGameId(gameId)
                savedAchievements.forEach { achievement ->
                    if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                        imageCacheManager.queueBadgeCache(achievement.id, achievement.badgeUrl, achievement.badgeUrlLock)
                    }
                }

                apiAchievements.map { achievement ->
                    val earned = earnedByBadgeId[achievement.badgeId]
                    val isUnlocked = earned != null
                    val isUnlockedHardcore = earned?.dateHardcore != null
                    AchievementUi(
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = if (isUnlocked) achievement.badgeUrl else (achievement.badgeUrlLock ?: achievement.badgeUrl),
                        isUnlocked = isUnlocked,
                        isUnlockedHardcore = isUnlockedHardcore
                    )
                }
            }
            is RomMResult.Error -> emptyList()
        }
    }

    private fun getEarnedAchievements(gameRaId: Long?): List<RomMEarnedAchievement> {
        if (gameRaId == null) return emptyList()
        return romMRepository.getEarnedAchievements(gameRaId)
    }

}
