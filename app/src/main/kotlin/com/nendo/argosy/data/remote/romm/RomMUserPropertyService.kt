package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import kotlinx.coroutines.flow.first
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val syncCoordinator: dagger.Lazy<SyncCoordinator>,
    private val userPreferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository
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

    suspend fun fetchUserScreenshots(rommId: Long): List<String> = withContext(Dispatchers.IO) {
        val currentApi = api ?: return@withContext emptyList()
        try {
            val response = currentApi.getRom(rommId)
            if (!response.isSuccessful) return@withContext emptyList()
            val shots = response.body()?.userScreenshots.orEmpty().filter { it.isGallery }
            shots.mapNotNull { shot ->
                val target = imageCacheManager.userScreenshotTargetFile(rommId, shot.id, shot.updatedAt ?: "")
                if (target.exists() && target.length() > 0) return@mapNotNull target.absolutePath
                val content = currentApi.downloadScreenshotContent(shot.id)
                val body = content.body()
                if (!content.isSuccessful || body == null) return@mapNotNull null
                body.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.length() > 0) {
                    imageCacheManager.pruneStaleUserScreenshots(rommId, shot.id, target)
                    target.absolutePath
                } else {
                    target.delete()
                    null
                }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "fetchUserScreenshots: exception for rommId $rommId: ${e.message}")
            emptyList()
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

            val boxArtEnabled = userPreferencesRepository.userPreferences.first().boxArtCacheEnabled
            val boxBackUrl = if (boxArtEnabled) {
                rom.ssMetadata?.box2dBackPath?.let { apiClient.buildResourceUrl(it) }
            } else null
            val boxSpineUrl = if (boxArtEnabled) {
                rom.ssMetadata?.box2dSidePath?.let { apiClient.buildResourceUrl(it) }
            } else null
            if (boxBackUrl != null) {
                imageCacheManager.queueBoxFaceCache(boxBackUrl, rom.id, rom.name, ImageCacheManager.BoxFace.BACK)
            }
            if (boxSpineUrl != null) {
                imageCacheManager.queueBoxFaceCache(boxSpineUrl, rom.id, rom.name, ImageCacheManager.BoxFace.SPINE)
            }

            val updatedGame = game.withRomMetadata(rom).copy(
                coverPath = coverUrl,
                backgroundPath = backgroundUrl,
                screenshotPaths = screenshotUrls.joinToString(","),
                boxBackPath = boxBackUrl ?: game.boxBackPath,
                boxSpinePath = boxSpineUrl ?: game.boxSpinePath
            )

            gameDao.update(updatedGame)
            RomMResult.Success(Unit)
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to refresh game data")
        }
    }
}
