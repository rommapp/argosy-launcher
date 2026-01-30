package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.ui.screens.gamedetail.toAchievementUi
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
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
    private val gameDao: GameDao,
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
        if (!hasRommId) {
            _achievements.value = emptyList()
            return
        }
        _achievements.value = achievementDao.getByGameId(gameId).map { it.toAchievementUi() }
    }

    fun refresh(scope: CoroutineScope, gameId: Long, rommId: Long?) {
        if (rommId == null) return

        scope.launch {
            _isLoading.value = true
            val fresh = fetchAndCacheAchievements(rommId, gameId)
            if (fresh.isNotEmpty()) {
                _achievements.value = fresh
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchAndCacheAchievements(rommId: Long, gameId: Long): List<AchievementUi> {
        val game = gameDao.getById(gameId)
        val raId = game?.raId

        if (raId != null) {
            val raAchievements = fetchAchievementsFromRA(raId, gameId)
            if (raAchievements.isNotEmpty()) {
                return raAchievements
            }
        }

        return fetchAchievementsFromRomM(rommId, gameId)
    }

    private suspend fun fetchAchievementsFromRA(raId: Long, gameId: Long): List<AchievementUi> {
        val raData = raRepository.getGameAchievementsWithProgress(raId) ?: return emptyList()

        val entities = raData.achievements.map { achievement ->
            val isUnlocked = achievement.id in raData.unlockedIds
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
                unlockedAt = if (isUnlocked) System.currentTimeMillis() else null,
                unlockedHardcoreAt = null
            )
        }
        achievementDao.replaceForGame(gameId, entities)
        gameDao.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())

        gameDao.updateAchievementCount(gameId, raData.totalCount, raData.earnedCount)
        achievementUpdateBus.emit(
            AchievementUpdateBus.AchievementUpdate(gameId, raData.totalCount, raData.earnedCount)
        )

        val savedAchievements = achievementDao.getByGameId(gameId)
        savedAchievements.forEach { achievement ->
            if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                imageCacheManager.queueBadgeCache(achievement.id, achievement.badgeUrl, achievement.badgeUrlLock)
            }
        }

        return raData.achievements.map { achievement ->
            val isUnlocked = achievement.id in raData.unlockedIds
            val badgeUrl = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/$it.png" }
            val badgeUrlLock = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/${it}_lock.png" }

            AchievementUi(
                raId = achievement.id,
                title = achievement.title,
                description = achievement.description ?: "",
                points = achievement.points,
                type = achievement.type,
                badgeUrl = if (isUnlocked) badgeUrl else (badgeUrlLock ?: badgeUrl),
                isUnlocked = isUnlocked,
                isUnlockedHardcore = false
            )
        }
    }

    private suspend fun fetchAchievementsFromRomM(rommId: Long, gameId: Long): List<AchievementUi> {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements ?: emptyList()
                if (apiAchievements.isEmpty()) return emptyList()

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
                gameDao.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())

                val earnedCount = entities.count { it.isUnlocked }
                gameDao.updateAchievementCount(gameId, entities.size, earnedCount)
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

    private fun parseTimestamp(timestamp: String): Long? {
        return try {
            java.time.ZonedDateTime.parse(timestamp, java.time.format.DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.Instant.parse(timestamp).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
