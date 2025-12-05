package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlatformStats(
    val platformId: String,
    val platformName: String,
    val totalGames: Int,
    val downloadedGames: Int,
    val downloadedSize: Long
)

@Singleton
class GameRepository @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao
) {
    suspend fun validateLocalFiles(): Int = withContext(Dispatchers.IO) {
        val gamesWithPaths = gameDao.getGamesWithLocalPath()
        var invalidated = 0
        gamesWithPaths.forEach { game ->
            game.localPath?.let { path ->
                if (!File(path).exists()) {
                    gameDao.clearLocalPath(game.id)
                    invalidated++
                }
            }
        }
        invalidated
    }

    suspend fun validateGameFile(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext false
        val path = game.localPath ?: return@withContext false
        if (!File(path).exists()) {
            gameDao.clearLocalPath(gameId)
            return@withContext false
        }
        true
    }

    suspend fun getDownloadedGamesSize(): Long = withContext(Dispatchers.IO) {
        val gamesWithPaths = gameDao.getGamesWithLocalPath()
        gamesWithPaths.sumOf { game ->
            game.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.length() else 0L
            } ?: 0L
        }
    }

    suspend fun getDownloadedGamesCount(): Int = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPath().size
    }

    suspend fun getGamesWithLocalPaths() = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPath()
    }

    suspend fun updateLocalPath(gameId: Long, newPath: String) = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext
        gameDao.updateLocalPath(gameId, newPath, game.source)
    }

    suspend fun clearLocalPath(gameId: Long) = withContext(Dispatchers.IO) {
        gameDao.clearLocalPath(gameId)
    }

    suspend fun getPlatformBreakdowns(): List<PlatformStats> = withContext(Dispatchers.IO) {
        val platforms = platformDao.observeAllPlatforms().first()
        val allGames = gameDao.observeAll().first()

        platforms.mapNotNull { platform ->
            val platformGames = allGames.filter { game -> game.platformId == platform.id }
            if (platformGames.isEmpty()) return@mapNotNull null

            val downloadedGames = platformGames.filter { game -> game.localPath != null }
            val downloadedSize = downloadedGames.sumOf { game ->
                game.localPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.length() else 0L
                } ?: 0L
            }

            PlatformStats(
                platformId = platform.id,
                platformName = platform.name,
                totalGames = platformGames.size,
                downloadedGames = downloadedGames.size,
                downloadedSize = downloadedSize
            )
        }.sortedByDescending { stats -> stats.totalGames }
    }
}
