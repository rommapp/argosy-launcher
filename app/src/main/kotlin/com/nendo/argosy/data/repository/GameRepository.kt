package com.nendo.argosy.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.nendo.argosy.data.emulator.M3uManager
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
        return normalizeForMatch(a) == normalizeForMatch(b)
    }

    private suspend fun findPrimaryRomInFolder(folder: File, platformSlug: String): File? {
        val rootFiles = folder.listFiles()?.filter { it.isFile } ?: return null
        if (rootFiles.isEmpty()) return null

        val m3uFile = rootFiles.find { it.extension.lowercase() == "m3u" }
        if (m3uFile != null) {
            return M3uManager.parseFirstDisc(m3uFile)
        }

        val platform = platformDao.getBySlug(platformSlug) ?: return null
        val validExtensions = platform.romExtensions
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        return rootFiles
            .filter { it.extension.lowercase() in validExtensions }
            .maxByOrNull { it.length() }
    }

    private suspend fun isGamePathValid(path: String, platformSlug: String): Boolean {
        val file = File(path)
        if (file.exists()) return true

        val parentFolder = file.parentFile ?: return false
        if (!parentFolder.exists() || !parentFolder.isDirectory) return false

        val platformFolder = parentFolder.parentFile
        if (platformFolder == null || !platformFolder.exists()) return false

        return isFolderValid(parentFolder, platformSlug)
    }

    private suspend fun isFolderValid(folder: File, platformSlug: String): Boolean {
        val rootFiles = folder.listFiles()?.filter { it.isFile } ?: return false
        if (rootFiles.isEmpty()) {
            Log.d(TAG, "Folder empty: ${folder.name}")
            return false
        }

        val m3uFile = rootFiles.find { it.extension.lowercase() == "m3u" }
        if (m3uFile != null) {
            val isComplete = M3uManager.isM3uComplete(m3uFile)
            if (!isComplete) {
                Log.d(TAG, "Folder incomplete: ${folder.name} - m3u has missing discs")
                return false
            }
            Log.d(TAG, "Folder valid (m3u): ${folder.name}")
            return true
        }

        val cueFile = rootFiles.find { it.extension.lowercase() == "cue" }
        if (cueFile != null) {
            val isComplete = M3uManager.isCueComplete(cueFile)
            if (!isComplete) {
                Log.d(TAG, "Folder incomplete: ${folder.name} - cue has missing bins")
                return false
            }
            Log.d(TAG, "Folder valid (cue): ${folder.name}")
            return true
        }

        val platform = platformDao.getBySlug(platformSlug) ?: return false
        val validExtensions = platform.romExtensions
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        val hasValidRom = rootFiles.any { it.extension.lowercase() in validExtensions }
        if (hasValidRom) {
            Log.d(TAG, "Folder valid (rom): ${folder.name}")
            return true
        }

        Log.d(TAG, "Folder invalid: ${folder.name} - no valid ROM files")
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

                val folderMatch = folders.find { folder -> titlesMatch(folder.name, game.title) }
                if (folderMatch != null) {
                    val primaryRom = findPrimaryRomInFolder(folderMatch, game.platformSlug)
                    if (primaryRom != null) {
                        gameDao.updateLocalPath(game.id, primaryRom.absolutePath, GameSource.ROMM_SYNCED)
                        discovered++
                        Log.d(TAG, "Discovered folder: ${game.title} -> ${folderMatch.name}/${primaryRom.name}")
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
        for (game in gamesWithPaths) {
            val path = game.localPath ?: continue
            if (!isGamePathValid(path, game.platformSlug)) {
                gameDao.clearLocalPath(game.id)
                invalidated++
                Log.d(TAG, "Invalidated: ${game.title} (path no longer valid: $path)")
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
                        val gameFile = findPrimaryRomInFolder(matchingFolder, game.platformSlug)
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
        isGamePathValid(path, game.platformSlug)
    }

    suspend fun validateAndDiscoverGame(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext false

        if (game.localPath != null) {
            if (isGamePathValid(game.localPath, game.platformSlug)) {
                return@withContext true
            }
            gameDao.clearLocalPath(gameId)
            Log.d(TAG, "Cleared invalid path for: ${game.title}")
        }

        if (game.rommId == null) return@withContext false

        val platformDir = getDownloadDir(game.platformSlug)
        if (!platformDir.exists()) return@withContext false

        val allEntries = platformDir.listFiles() ?: return@withContext false
        val files = allEntries.filter { it.isFile && !it.name.endsWith(".tmp") }
        val folders = allEntries.filter { it.isDirectory }

        val fileMatch = files.find { file -> titlesMatch(file.nameWithoutExtension, game.title) }
        if (fileMatch != null) {
            gameDao.updateLocalPath(gameId, fileMatch.absolutePath, GameSource.ROMM_SYNCED)
            Log.d(TAG, "Discovered file for ${game.title}: ${fileMatch.name}")
            return@withContext true
        }

        val folderMatch = folders.find { folder -> titlesMatch(folder.name, game.title) }
        if (folderMatch != null) {
            val primaryRom = findPrimaryRomInFolder(folderMatch, game.platformSlug)
            if (primaryRom != null) {
                gameDao.updateLocalPath(gameId, primaryRom.absolutePath, GameSource.ROMM_SYNCED)
                Log.d(TAG, "Discovered folder for ${game.title}: ${folderMatch.name}/${primaryRom.name}")
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
