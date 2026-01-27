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
    val platformId: Long,
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

    private suspend fun getDownloadDir(platformSlug: String): File {
        val platform = platformDao.getBySlug(platformSlug)
        if (platform?.customRomPath != null) {
            return File(platform.customRomPath).also { it.mkdirs() }
        }

        val prefs = preferencesRepository.userPreferences.first()
        val customPath = prefs.romStoragePath
        return if (customPath != null) {
            File(customPath, platformSlug).also { it.mkdirs() }
        } else {
            File(defaultDownloadDir, platformSlug).also { it.mkdirs() }
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

    private fun normalizeForMatch(name: String): String {
        return name
            .replace(Regex("[\\\\:*?\"<>|/]"), "_")
            .replace(Regex("\\s+"), " ")
            .lowercase()
            .trim()
    }

    private fun titlesMatch(a: String, b: String): Boolean {
        val normA = normalizeForMatch(a)
        val normB = normalizeForMatch(b)
        return normA == normB || normA.contains(normB) || normB.contains(normA)
    }

    private suspend fun findLargestRomInFolder(folder: File, platformSlug: String): File? {
        val platform = platformDao.getBySlug(platformSlug) ?: return null
        val validExtensions = platform.romExtensions
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        // Only look at root files (no subdirectories), filter by valid ROM extensions
        val romFiles = folder.listFiles()
            ?.filter { it.isFile }
            ?.filter { file ->
                val ext = file.extension.lowercase()
                ext in validExtensions
            }
            ?: return null

        // Return largest file
        return romFiles.maxByOrNull { it.length() }
    }

    private fun isGamePathValid(path: String): Boolean {
        val file = File(path)
        if (file.exists()) return true

        // If file doesn't exist, check if parent folder exists with content
        // This handles folder-based ROMs where the specific file path may have changed
        val parentFolder = file.parentFile ?: return false
        if (!parentFolder.exists() || !parentFolder.isDirectory) return false

        // Check if the parent is a game folder (not the platform root)
        // Game folders are inside platform folders, so parent of parent should exist
        val platformFolder = parentFolder.parentFile
        if (platformFolder == null || !platformFolder.exists()) return false

        // Parent folder has files - the game folder is still valid
        val hasFiles = parentFolder.listFiles()?.any { it.isFile } ?: false
        if (hasFiles) {
            Log.d(TAG, "Folder-based game valid: ${parentFolder.name} (specific file missing but folder exists with content)")
            return true
        }

        return false
    }

    suspend fun discoverLocalFiles(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "discoverLocalFiles: storage not ready, skipping")
            return@withContext 0
        }

        val startTime = System.currentTimeMillis()
        val gamesWithoutPath = gameDao.getGamesWithRommIdButNoPath()
        if (gamesWithoutPath.isEmpty()) return@withContext 0

        // Group by platform to avoid redundant listFiles() calls
        val gamesByPlatform = gamesWithoutPath.groupBy { it.platformSlug }
        var discovered = 0

        for ((platformSlug, games) in gamesByPlatform) {
            val platformDir = getDownloadDir(platformSlug)
            if (!platformDir.exists()) continue

            val allEntries = platformDir.listFiles() ?: continue
            val files = allEntries.filter { it.isFile && !it.name.endsWith(".tmp") }
            val folders = allEntries.filter { it.isDirectory }

            for (game in games) {
                // Check direct file matches first
                val fileMatch = files.find { file -> titlesMatch(file.nameWithoutExtension, game.title) }
                if (fileMatch != null) {
                    gameDao.updateLocalPath(game.id, fileMatch.absolutePath, GameSource.ROMM_SYNCED)
                    discovered++
                    Log.d(TAG, "Discovered file: ${game.title} -> ${fileMatch.name}")
                    continue
                }

                // Check folder matches (folder-based ROMs like Switch)
                val folderMatch = folders.find { folder -> titlesMatch(folder.name, game.title) }
                if (folderMatch != null) {
                    val gameFile = findLargestRomInFolder(folderMatch, game.platformSlug)
                    if (gameFile != null) {
                        gameDao.updateLocalPath(game.id, gameFile.absolutePath, GameSource.ROMM_SYNCED)
                        discovered++
                        Log.d(TAG, "Discovered folder: ${game.title} -> ${folderMatch.name}/${gameFile.name}")
                    }
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Discovery complete: $discovered files found in ${elapsed}ms")
        discovered
    }

    suspend fun validateLocalFiles(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "validateLocalFiles: storage not ready, skipping")
            return@withContext 0
        }

        val startTime = System.currentTimeMillis()
        val gamesWithPaths = gameDao.getGamesWithLocalPath()
        var invalidated = 0
        gamesWithPaths.forEach { game ->
            game.localPath?.let { path ->
                if (!isGamePathValid(path)) {
                    gameDao.clearLocalPath(game.id)
                    invalidated++
                    Log.d(TAG, "Invalidated: ${game.title} (path no longer valid: $path)")
                }
            }
        }
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Validation complete: checked ${gamesWithPaths.size} games, $invalidated invalidated in ${elapsed}ms")
        invalidated
    }

    suspend fun recoverDownloadPaths(): Int = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val gamesWithoutPath = gameDao.getGamesWithRommIdButNoPath()
        if (gamesWithoutPath.isEmpty()) return@withContext 0

        var recovered = 0

        for (game in gamesWithoutPath) {
            val rommId = game.rommId ?: continue

            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> {
                    val rom = result.data
                    val fileName = rom.fileName ?: continue

                    val platformDir = getDownloadDir(game.platformSlug)

                    // Check direct file first
                    val expectedFile = File(platformDir, fileName)
                    if (expectedFile.exists()) {
                        gameDao.updateLocalPath(game.id, expectedFile.absolutePath, GameSource.ROMM_SYNCED)
                        recovered++
                        Log.d(TAG, "Recovered download path for: ${game.title}")
                        continue
                    }

                    // Check if game is in a titled folder
                    val folders = platformDir.listFiles()?.filter { it.isDirectory } ?: continue
                    val matchingFolder = folders.find { folder -> titlesMatch(folder.name, game.title) }
                    if (matchingFolder != null) {
                        val gameFile = findLargestRomInFolder(matchingFolder, game.platformSlug)
                        if (gameFile != null) {
                            gameDao.updateLocalPath(game.id, gameFile.absolutePath, GameSource.ROMM_SYNCED)
                            recovered++
                            Log.d(TAG, "Recovered folder path for: ${game.title} -> ${matchingFolder.name}/${gameFile.name}")
                        }
                    }
                }
                is RomMResult.Error -> {
                    Log.w(TAG, "Failed to get ROM info for ${game.title}: ${result.message}")
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Download recovery complete: $recovered paths recovered in ${elapsed}ms")
        recovered
    }

    suspend fun checkGameFileExists(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext false
        val path = game.localPath ?: return@withContext false
        isGamePathValid(path)
    }

    suspend fun validateAndDiscoverGame(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext false

        // If has path, validate it
        if (game.localPath != null) {
            if (isGamePathValid(game.localPath)) {
                return@withContext true
            }
            // Path invalid, clear it
            gameDao.clearLocalPath(gameId)
            Log.d(TAG, "Cleared invalid path for: ${game.title}")
        }

        // No valid path - try to discover
        if (game.rommId == null) return@withContext false

        val platformDir = getDownloadDir(game.platformSlug)
        if (!platformDir.exists()) return@withContext false

        val allEntries = platformDir.listFiles() ?: return@withContext false

        // Check for direct file match first
        val fileMatch = allEntries
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .find { file -> titlesMatch(file.nameWithoutExtension, game.title) }

        if (fileMatch != null) {
            gameDao.updateLocalPath(gameId, fileMatch.absolutePath, GameSource.ROMM_SYNCED)
            Log.d(TAG, "Discovered file for ${game.title}: ${fileMatch.name}")
            return@withContext true
        }

        // Check for folder match (game organized in subfolder)
        val folderMatch = allEntries
            .filter { it.isDirectory }
            .find { folder -> titlesMatch(folder.name, game.title) }

        if (folderMatch != null) {
            val gameFile = findLargestRomInFolder(folderMatch, game.platformSlug)
            if (gameFile != null) {
                gameDao.updateLocalPath(gameId, gameFile.absolutePath, GameSource.ROMM_SYNCED)
                Log.d(TAG, "Discovered folder for ${game.title}: ${folderMatch.name}/${gameFile.name}")
                return@withContext true
            }
        }

        false
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

    suspend fun getGamesWithLocalPathsForPlatform(platformId: Long) = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPath().filter { it.platformId == platformId }
    }

    suspend fun getDownloadDirForPlatform(platformSlug: String): File = withContext(Dispatchers.IO) {
        getDownloadDir(platformSlug)
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

    suspend fun cleanupEmptyNumericFolders(): Int = withContext(Dispatchers.IO) {
        val downloadDir = getGlobalDownloadDir()
        if (!downloadDir.exists()) return@withContext 0

        var removed = 0
        downloadDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
            if (dir.name.toLongOrNull() != null && dir.listFiles().isNullOrEmpty()) {
                if (dir.delete()) {
                    removed++
                    Log.d(TAG, "Removed empty numeric folder: ${dir.name}")
                }
            }
        }
        removed
    }
}
