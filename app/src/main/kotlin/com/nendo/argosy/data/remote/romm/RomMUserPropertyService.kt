package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMUserPropertyService"

@Singleton
class RomMUserPropertyService @Inject constructor(
    private val apiClient: RomMApiClient,
    private val connectionManager: RomMConnectionManager,
    private val gameDao: GameDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val imageCacheManager: ImageCacheManager,
    private val syncCoordinator: dagger.Lazy<SyncCoordinator>
) {
    private val api: RomMApi? get() = connectionManager.getApi()

    suspend fun updateUserRating(gameId: Long, rating: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        gameDao.updateUserRating(gameId, rating)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.RATING, intValue = rating)
        return RomMResult.Success(Unit)
    }

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        gameDao.updateUserDifficulty(gameId, difficulty)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.DIFFICULTY, intValue = difficulty)
        return RomMResult.Success(Unit)
    }

    suspend fun updateUserStatus(gameId: Long, status: String?): RomMResult<Unit> {
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        gameDao.updateStatus(gameId, status)
        val rommId = game.rommId ?: return RomMResult.Success(Unit)
        syncCoordinator.get().queuePropertyChange(gameId, rommId, SyncType.STATUS, stringValue = status)
        return RomMResult.Success(Unit)
    }

    suspend fun refreshUserProps(gameId: Long): RomMResult<Unit> {
        val currentApi = api ?: return RomMResult.Success(Unit)
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        val rommId = game.rommId ?: return RomMResult.Success(Unit)

        return try {
            val response = currentApi.getRom(rommId)
            if (!response.isSuccessful) {
                Logger.warn(TAG, "refreshUserProps: failed to fetch rom $rommId: ${response.code()}")
                return RomMResult.Success(Unit)
            }

            val rom = response.body() ?: return RomMResult.Success(Unit)
            val romUser = rom.romUser ?: return RomMResult.Success(Unit)

            val hasRating = pendingSyncQueueDao.hasPending(gameId, SyncType.RATING)
            val hasDifficulty = pendingSyncQueueDao.hasPending(gameId, SyncType.DIFFICULTY)
            val hasStatus = pendingSyncQueueDao.hasPending(gameId, SyncType.STATUS)

            val updatedGame = game.copy(
                userRating = if (hasRating) game.userRating else romUser.rating,
                userDifficulty = if (hasDifficulty) game.userDifficulty else romUser.difficulty,
                status = if (hasStatus) game.status else romUser.status,
                backlogged = romUser.backlogged,
                nowPlaying = romUser.nowPlaying
            )

            if (updatedGame != game) {
                gameDao.update(updatedGame)
            }

            RomMResult.Success(Unit)
        } catch (e: Exception) {
            Logger.warn(TAG, "refreshUserProps: exception for game $gameId: ${e.message}")
            RomMResult.Success(Unit)
        }
    }

    suspend fun refreshGameData(gameId: Long): RomMResult<Unit> {
        val currentApi = api ?: return RomMResult.Error("Not connected")
        val game = gameDao.getById(gameId) ?: return RomMResult.Error("Game not found")
        val rommId = game.rommId ?: return RomMResult.Error("Not a RomM game")

        return try {
            val response = currentApi.getRom(rommId)
            if (!response.isSuccessful) {
                return RomMResult.Error("Failed to fetch ROM data", response.code())
            }

            val rom = response.body() ?: return RomMResult.Error("Empty response")

            imageCacheManager.deleteGameImages(rommId)

            val screenshotUrls = rom.screenshotUrls.ifEmpty {
                rom.screenshotPaths?.map { apiClient.buildMediaUrl(it) } ?: emptyList()
            }

            val backgroundUrl = rom.backgroundUrls.firstOrNull()
                ?: screenshotUrls.getOrNull(1)
                ?: screenshotUrls.getOrNull(0)
            val coverUrl = rom.coverLarge?.let { apiClient.buildMediaUrl(it) }

            if (backgroundUrl != null) {
                imageCacheManager.queueBackgroundCache(backgroundUrl, rom.id, rom.name)
            }
            if (coverUrl != null) {
                imageCacheManager.queueCoverCache(coverUrl, rom.id, rom.name)
            }

            val updatedGame = game.copy(
                title = rom.name,
                sortTitle = RomMUtils.createSortTitle(rom.name),
                coverPath = coverUrl,
                backgroundPath = backgroundUrl,
                screenshotPaths = screenshotUrls.joinToString(","),
                description = rom.summary,
                releaseYear = rom.firstReleaseDateMillis?.let {
                    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).year
                },
                genre = rom.genres?.firstOrNull(),
                developer = rom.companies?.firstOrNull(),
                rating = rom.metadatum?.averageRating,
                regions = rom.regions?.joinToString(","),
                languages = rom.languages?.joinToString(","),
                gameModes = rom.metadatum?.gameModes?.joinToString(","),
                franchises = rom.metadatum?.franchises?.joinToString(","),
                achievementCount = rom.raMetadata?.achievements?.size ?: game.achievementCount
            )

            gameDao.update(updatedGame)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to refresh game data")
        }
    }
}
