package com.nendo.argosy.data.store

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.screens.gamedetail.AchievementUi
import com.nendo.argosy.ui.screens.gamedetail.toAchievementUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class GameDetailData(
    val game: GameEntity,
    val platformName: String,
    val achievements: List<AchievementUi> = emptyList(),
    val lastRefreshed: Long = System.currentTimeMillis(),
    val isStale: Boolean = false
)

@Singleton
class GameDetailStore @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val achievementDao: AchievementDao,
    private val romMRepository: RomMRepository,
    private val imageCacheManager: ImageCacheManager
) {
    private val _cache = MutableStateFlow<Map<Long, GameDetailData>>(emptyMap())
    private val refreshMutex = Mutex()

    companion object {
        private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Observe game detail data. Returns cached data immediately if available,
     * or null if not yet loaded.
     */
    fun observe(gameId: Long): Flow<GameDetailData?> =
        _cache.map { it[gameId] }.distinctUntilChanged()

    /**
     * Check if we have cached data for a game.
     */
    fun hasCached(gameId: Long): Boolean = _cache.value.containsKey(gameId)

    /**
     * Get cached data synchronously (for initial UI state).
     */
    fun getCached(gameId: Long): GameDetailData? = _cache.value[gameId]

    /**
     * Prepopulate cache from a list screen (Home, Library).
     * This avoids DB hits when navigating to detail.
     */
    suspend fun prepopulate(gameId: Long) {
        if (hasCached(gameId)) return

        val game = gameDao.getById(gameId) ?: return
        val platform = platformDao.getById(game.platformId)
        val achievements = if (game.rommId != null) {
            achievementDao.getByGameId(gameId).map { it.toAchievementUi() }
        } else emptyList()

        _cache.update { current ->
            current + (gameId to GameDetailData(
                game = game,
                platformName = platform?.name ?: "Unknown",
                achievements = achievements,
                isStale = true // Mark as stale so detail view knows to refresh
            ))
        }
    }

    /**
     * Load fresh data from DB. Called when entering detail view.
     * Skips if data is fresh enough.
     */
    suspend fun load(gameId: Long, forceRefresh: Boolean = false): GameDetailData? {
        val existing = _cache.value[gameId]
        val isFresh = existing != null &&
            !existing.isStale &&
            (System.currentTimeMillis() - existing.lastRefreshed) < STALE_THRESHOLD_MS

        if (isFresh && !forceRefresh) {
            return existing
        }

        val game = gameDao.getById(gameId) ?: return null
        val platform = platformDao.getById(game.platformId)
        val achievements = if (game.rommId != null) {
            achievementDao.getByGameId(gameId).map { it.toAchievementUi() }
        } else emptyList()

        val data = GameDetailData(
            game = game,
            platformName = platform?.name ?: "Unknown",
            achievements = achievements,
            isStale = false
        )

        _cache.update { it + (gameId to data) }
        return data
    }

    /**
     * Refresh from remote API. Called for background refresh of achievements, etc.
     * Uses mutex to prevent duplicate concurrent refreshes.
     */
    suspend fun refreshFromRemote(gameId: Long) {
        if (!refreshMutex.tryLock()) return

        try {
            val game = gameDao.getById(gameId) ?: return

            // Refresh achievements if applicable
            if (game.rommId != null) {
                val achievements = fetchFreshAchievements(gameId, game.rommId, game.raId)
                if (achievements.isNotEmpty()) {
                    _cache.update { current ->
                        val existing = current[gameId] ?: return@update current
                        current + (gameId to existing.copy(
                            achievements = achievements,
                            lastRefreshed = System.currentTimeMillis()
                        ))
                    }
                }
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    /**
     * Update specific fields without full reload.
     * Useful after actions like favorite toggle, rating change, etc.
     */
    fun updateGame(gameId: Long, transform: (GameEntity) -> GameEntity) {
        _cache.update { current ->
            val existing = current[gameId] ?: return@update current
            current + (gameId to existing.copy(
                game = transform(existing.game),
                lastRefreshed = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Invalidate cache entry. Next observe() will return stale data,
     * prompting a refresh.
     */
    fun invalidate(gameId: Long) {
        _cache.update { current ->
            val existing = current[gameId] ?: return@update current
            current + (gameId to existing.copy(isStale = true))
        }
    }

    /**
     * Clear cache entry entirely.
     */
    fun evict(gameId: Long) {
        _cache.update { it - gameId }
    }

    /**
     * Clear all cached data.
     */
    fun clear() {
        _cache.value = emptyMap()
    }

    private suspend fun fetchFreshAchievements(
        gameId: Long,
        rommId: Long,
        raId: Long?
    ): List<AchievementUi> {
        // Try RA first if we have credentials
        if (raId != null) {
            // RA fetch would go here - simplified for PoC
        }

        // Fall back to cached DB data for now
        // Full implementation would fetch from RomM API
        return achievementDao.getByGameId(gameId).map { it.toAchievementUi() }
    }
}
