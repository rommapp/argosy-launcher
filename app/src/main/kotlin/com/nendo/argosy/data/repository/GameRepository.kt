package com.nendo.argosy.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameRepository"

data class PlatformStats(
    val platformId: String,
    val platformName: String,
    val totalGames: Int,
    val downloadedGames: Int,
    val downloadedSize: Long
)

@Singleton
class GameRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val defaultDownloadDir: File by lazy {
        File(context.getExternalFilesDir(null), "downloads")
    }

    private suspend fun getDownloadDir(): File {
        val prefs = preferencesRepository.userPreferences.first()
        val customPath = prefs.romStoragePath
        return if (customPath != null) File(customPath) else defaultDownloadDir
    }

    private fun isUserUnlocked(): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        return userManager.isUserUnlocked
    }

    suspend fun awaitUserUnlocked() {
        if (isUserUnlocked()) {
            Log.d(TAG, "Device already unlocked, proceeding immediately")
            return
        }

        Log.d(TAG, "Device not unlocked, waiting for ACTION_USER_UNLOCKED")
        suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    Log.d(TAG, "Received ACTION_USER_UNLOCKED")
                    context.unregisterReceiver(this)
                    continuation.resume(Unit)
                }
            }
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {}
            }
        }
    }

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

    suspend fun recoverDownloadPaths(): Int = withContext(Dispatchers.IO) {
        val gamesWithoutPath = gameDao.getGamesWithRommIdButNoPath()
        if (gamesWithoutPath.isEmpty()) return@withContext 0

        val downloadDir = getDownloadDir()
        var recovered = 0

        for (game in gamesWithoutPath) {
            val rommId = game.rommId ?: continue

            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> {
                    val rom = result.data
                    val fileName = rom.fileName ?: continue
                    val platformSlug = rom.platformSlug

                    val expectedFile = File(downloadDir, "$platformSlug/$fileName")
                    if (expectedFile.exists()) {
                        gameDao.updateLocalPath(game.id, expectedFile.absolutePath, GameSource.ROMM_SYNCED)
                        recovered++
                        Log.d(TAG, "Recovered download path for: ${game.title}")
                    }
                }
                is RomMResult.Error -> {
                    Log.w(TAG, "Failed to get ROM info for ${game.title}: ${result.message}")
                }
            }
        }

        Log.d(TAG, "Download recovery complete: $recovered paths recovered")
        recovered
    }

    suspend fun checkGameFileExists(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext false
        val path = game.localPath ?: return@withContext false
        File(path).exists()
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
