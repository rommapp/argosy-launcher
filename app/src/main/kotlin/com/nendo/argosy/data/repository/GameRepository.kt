package com.nendo.argosy.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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

    private suspend fun getDownloadDir(platformId: String): File {
        val platform = platformDao.getById(platformId)
        if (platform?.customRomPath != null) {
            return File(platform.customRomPath).also { it.mkdirs() }
        }

        val prefs = preferencesRepository.userPreferences.first()
        val customPath = prefs.romStoragePath
        return if (customPath != null) {
            File(customPath, platformId).also { it.mkdirs() }
        } else {
            File(defaultDownloadDir, platformId).also { it.mkdirs() }
        }
    }

    private suspend fun getGlobalDownloadDir(): File {
        val prefs = preferencesRepository.userPreferences.first()
        return prefs.romStoragePath?.let { File(it) } ?: defaultDownloadDir
    }

    private fun isStorageReady(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            if (!userManager.isUserUnlocked) return false
        }
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    suspend fun awaitStorageReady(timeoutMs: Long = 10_000L): Boolean {
        if (isStorageReady()) {
            Log.d(TAG, "Storage already ready")
            return true
        }

        Log.d(TAG, "Storage not ready, waiting up to ${timeoutMs}ms")

        return withTimeoutOrNull(timeoutMs) {
            while (!isStorageReady()) {
                delay(500)
            }
            true
        } ?: run {
            Log.w(TAG, "Timeout waiting for storage")
            false
        }
    }

    suspend fun discoverLocalFiles(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "discoverLocalFiles: storage not ready, skipping")
            return@withContext 0
        }

        val gamesWithoutPath = gameDao.getGamesWithRommIdButNoPath()
        if (gamesWithoutPath.isEmpty()) return@withContext 0

        var discovered = 0
        for (game in gamesWithoutPath) {
            val platformDir = getDownloadDir(game.platformId)
            if (!platformDir.exists()) continue

            val candidates = platformDir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") } ?: continue
            val titleLower = game.title.lowercase()

            val match = candidates.find { file ->
                val name = file.nameWithoutExtension.lowercase()
                name == titleLower || name.contains(titleLower) || titleLower.contains(name)
            }

            if (match != null) {
                gameDao.updateLocalPath(game.id, match.absolutePath, GameSource.ROMM_SYNCED)
                discovered++
                Log.d(TAG, "Discovered: ${game.title} -> ${match.name}")
            }
        }

        Log.d(TAG, "Discovery complete: $discovered files found")
        discovered
    }

    suspend fun validateLocalFiles(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "validateLocalFiles: storage not ready, skipping")
            return@withContext 0
        }

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

        var recovered = 0

        for (game in gamesWithoutPath) {
            val rommId = game.rommId ?: continue

            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> {
                    val rom = result.data
                    val fileName = rom.fileName ?: continue
                    val platformSlug = rom.platformSlug

                    val platformDir = getDownloadDir(platformSlug)
                    val expectedFile = File(platformDir, fileName)
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

    suspend fun getAvailableStorageBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val downloadDir = getGlobalDownloadDir()
            downloadDir.mkdirs()
            val stat = android.os.StatFs(downloadDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun getGamesWithLocalPaths() = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPath()
    }

    suspend fun getGamesWithLocalPathsForPlatform(platformId: String) = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPath().filter { it.platformId == platformId }
    }

    suspend fun getDownloadDirForPlatform(platformId: String): File = withContext(Dispatchers.IO) {
        getDownloadDir(platformId)
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
