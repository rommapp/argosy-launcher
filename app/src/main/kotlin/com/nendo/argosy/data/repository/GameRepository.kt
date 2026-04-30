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
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.SearchCandidate
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
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
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val fileAccessLayer: com.nendo.argosy.data.storage.FileAccessLayer
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

    /**
     * Match a local file against a RomM-reported filename (game.rommFileName).
     * RomM stores the canonical filename its server has on disk, so users who
     * point Argosy at an existing RomM-synced library (e.g. ES-DE-style flat
     * folders) get exact matches including region and revision suffixes.
     * Compares both with and without extension in case Argosy post-processed
     * the download (e.g. rename-by-magic turning .zip into .chd).
     */
    private fun filenamesMatch(localName: String, rommFileName: String?): Boolean {
        if (rommFileName.isNullOrBlank()) return false
        if (localName.equals(rommFileName, ignoreCase = true)) return true
        val localStem = localName.substringBeforeLast('.')
        val rommStem = rommFileName.substringBeforeLast('.')
        return localStem.isNotEmpty() && localStem.equals(rommStem, ignoreCase = true)
    }

    /**
     * Single source of truth for "which local file belongs to this game?" -- prefer
     * the exact RomM-reported filename and fall back to title-normalized matching
     * for legacy entries (pre-beta.44 rommFileName) or user-downloaded files that
     * weren't synced from RomM.
     */
    private fun findLocalFileForGame(files: List<File>, game: GameEntity): File? {
        files.find { filenamesMatch(it.name, game.rommFileName) }?.let { return it }
        return files.find { titlesMatch(it.nameWithoutExtension, game.title) }
    }

    private suspend fun findPrimaryRomInFolder(folder: File, platformSlug: String): File? {
        val rootEntries = folder.listFiles() ?: return null
        val rootFiles = rootEntries.filter { it.isFile }

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

        val topLevelMatch = rootFiles
            .filter { it.extension.lowercase() in validExtensions }
            .maxByOrNull { it.length() }
        if (topLevelMatch != null) return topLevelMatch

        if (platformSlug == "wiiu") {
            val codeDir = rootEntries.firstOrNull { it.isDirectory && it.name.equals("code", ignoreCase = true) }
            if (codeDir != null) {
                val rpx = codeDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("rpx", ignoreCase = true) }
                    ?.maxByOrNull { it.length() }
                if (rpx != null) return rpx
            }
        }

        return null
    }

    private suspend fun resolveFileFallback(originalPath: String, platformSlug: String): String? {
        val fileName = File(originalPath).name
        val candidateDirs = buildList {
            // Per-platform custom path
            val platform = platformDao.getBySlug(platformSlug)
            if (platform?.customRomPath != null) {
                add(File(platform.customRomPath))
            }
            // Global custom path
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.romStoragePath != null) {
                add(File(prefs.romStoragePath, platformSlug))
            }
            // Default path
            add(File(defaultDownloadDir, platformSlug))
        }

        for (dir in candidateDirs) {
            if (!dir.exists()) continue
            // Direct file match
            val candidate = File(dir, fileName)
            if (candidate.exists()) return candidate.absolutePath
            // Folder match (multi-disc games)
            val folderName = File(originalPath).parentFile?.name
            if (folderName != null) {
                val folderCandidate = File(dir, folderName)
                if (folderCandidate.isDirectory) {
                    val fileInFolder = File(folderCandidate, fileName)
                    if (fileInFolder.exists()) return fileInFolder.absolutePath
                }
            }
        }
        return null
    }

    private fun isGamePathValid(path: String, platformSlug: String): Boolean {
        return File(path).exists()
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
                val fileMatch = findLocalFileForGame(files, game)
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
                    val entries = platformDir.listFiles() ?: continue
                    val files = entries.filter { it.isFile && !it.name.endsWith(".tmp") }
                    val folders = entries.filter { it.isDirectory }

                    // Exact direct-child first (cheapest), then case-insensitive
                    // filename match (covers rename-by-magic), then folder by title.
                    val expectedFile = File(platformDir, fileName)
                    val fileMatch = expectedFile.takeIf { it.exists() }
                        ?: files.find { filenamesMatch(it.name, fileName) }
                    if (fileMatch != null) {
                        gameDao.updateLocalPath(game.id, fileMatch.absolutePath, GameSource.ROMM_SYNCED)
                        recovered++
                        Log.d(TAG, "Recovered download path for: ${game.title} -> ${fileMatch.name}")
                        continue
                    }

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

        val fileMatch = findLocalFileForGame(files, game)
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

    suspend fun validateLocalFilesForPlatform(platformId: Long): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) return@withContext 0

        val games = gameDao.getGamesWithLocalPathByPlatform(platformId)
        var invalidated = 0
        for (game in games) {
            val path = game.localPath ?: continue
            if (!isGamePathValid(path, game.platformSlug)) {
                val resolved = resolveFileFallback(path, game.platformSlug)
                if (resolved != null) {
                    gameDao.updateLocalPath(game.id, resolved, game.source)
                    Log.d(TAG, "Resolved (fallback): ${game.title} -> $resolved")
                } else {
                    gameDao.clearLocalPath(game.id)
                    invalidated++
                    Log.d(TAG, "Invalidated (platform): ${game.title}")
                }
            }
        }
        invalidated
    }

    suspend fun deleteLocalFilesForPlatform(platformId: Long): Int = withContext(Dispatchers.IO) {
        val games = gameDao.getDownloadedByPlatform(platformId)
        var deleted = 0
        for (game in games) {
            val path = game.localPath ?: continue
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
                gameDao.clearLocalPath(game.id)
                deleted++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete ${game.title}: ${e.message}")
            }
        }
        Log.i(TAG, "Deleted $deleted local files for platform $platformId")
        deleted
    }

    suspend fun discoverLocalFilesForPlatform(platformId: Long): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) return@withContext 0

        val games = gameDao.getGamesWithRommIdButNoPathByPlatform(platformId)
        if (games.isEmpty()) return@withContext 0

        val gamesByPlatform = games.groupBy { it.platformSlug }
        var discovered = 0

        for ((platformSlug, platformGames) in gamesByPlatform) {
            val platformDir = getDownloadDir(platformSlug)
            if (!platformDir.exists()) continue

            val allEntries = platformDir.listFiles() ?: continue
            val files = allEntries.filter { it.isFile && !it.name.endsWith(".tmp") }
            val folders = allEntries.filter { it.isDirectory }

            for (game in platformGames) {
                val fileMatch = findLocalFileForGame(files, game)
                if (fileMatch != null) {
                    gameDao.updateLocalPath(game.id, fileMatch.absolutePath, GameSource.ROMM_SYNCED)
                    discovered++
                    continue
                }

                val folderMatch = folders.find { folder -> titlesMatch(folder.name, game.title) }
                if (folderMatch != null) {
                    val primaryRom = findPrimaryRomInFolder(folderMatch, game.platformSlug)
                    if (primaryRom != null) {
                        gameDao.updateLocalPath(game.id, primaryRom.absolutePath, GameSource.ROMM_SYNCED)
                        discovered++
                    }
                }
            }
        }
        discovered
    }

    suspend fun validateDiscLocalFiles(platformId: Long): Int = withContext(Dispatchers.IO) {
        val discs = gameDiscDao.getDiscsWithLocalPathByPlatform(platformId)
        var invalidated = 0
        for (disc in discs) {
            val path = disc.localPath ?: continue
            if (!File(path).exists()) {
                gameDiscDao.clearLocalPath(disc.id)
                invalidated++
            }
        }
        invalidated
    }

    suspend fun validateFileLocalFiles(platformId: Long): Int = withContext(Dispatchers.IO) {
        val files = gameFileDao.getFilesWithLocalPathByPlatform(platformId)
        var invalidated = 0
        for (file in files) {
            val path = file.localPath ?: continue
            if (!File(path).exists()) {
                gameFileDao.clearLocalPath(file.id)
                invalidated++
            }
        }
        invalidated
    }

    suspend fun ensureImagePathValid(gameId: Long): GameEntity? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        var changed = false

        if (game.coverPath?.startsWith("/") == true && !File(game.coverPath).exists()) {
            gameDao.clearCoverPath(gameId)
            changed = true
        }
        if (game.backgroundPath?.startsWith("/") == true && !File(game.backgroundPath).exists()) {
            gameDao.clearBackgroundPath(gameId)
            changed = true
        }

        if (changed) gameDao.getById(gameId) else game
    }

    // --- Direct DAO delegations ---

    suspend fun getById(id: Long): GameEntity? = gameDao.getById(id)

    fun observeById(id: Long): Flow<GameEntity?> = gameDao.observeById(id)

    suspend fun getPlayedGames(): List<GameEntity> = gameDao.getPlayedGames()

    suspend fun getRecentlyPlayed(limit: Int = 20): List<GameEntity> =
        gameDao.getRecentlyPlayed(limit)

    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<GameEntity>> =
        gameDao.observeRecentlyPlayed(limit)

    suspend fun getFavorites(): List<GameEntity> = gameDao.getFavorites()

    suspend fun getRandomGame(): GameEntity? = gameDao.getRandomGame()

    suspend fun getSearchCandidates(): List<SearchCandidate> =
        gameDao.getSearchCandidates()

    suspend fun getByIds(ids: List<Long>): List<GameEntity> = gameDao.getByIds(ids)

    suspend fun updateUserRating(gameId: Long, rating: Int) =
        gameDao.updateUserRating(gameId, rating)

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int) =
        gameDao.updateUserDifficulty(gameId, difficulty)

    suspend fun updateStatus(gameId: Long, status: String?) =
        gameDao.updateStatus(gameId, status)

    suspend fun updateFavorite(gameId: Long, favorite: Boolean) =
        gameDao.updateFavorite(gameId, favorite)

    suspend fun updateHidden(gameId: Long, hidden: Boolean) =
        gameDao.updateHidden(gameId, hidden)

    suspend fun getActiveSaveTimestamp(gameId: Long): Long? =
        gameDao.getActiveSaveTimestamp(gameId)

    suspend fun updateActiveSaveChannel(gameId: Long, channelName: String?) =
        gameDao.updateActiveSaveChannel(gameId, channelName)

    suspend fun updateActiveSaveTimestamp(gameId: Long, timestamp: Long?) =
        gameDao.updateActiveSaveTimestamp(gameId, timestamp)

    suspend fun updateActiveSaveApplied(gameId: Long, applied: Boolean) =
        gameDao.updateActiveSaveApplied(gameId, applied)

    suspend fun getActiveSaveChannel(gameId: Long): String? =
        gameDao.getActiveSaveChannel(gameId)

    suspend fun getBySource(source: GameSource): List<GameEntity> =
        gameDao.getBySource(source)

    suspend fun getByPackageName(packageName: String): GameEntity? =
        gameDao.getByPackageName(packageName)

    suspend fun delete(game: GameEntity) = gameDao.delete(game)

    suspend fun delete(gameId: Long) = gameDao.delete(gameId)

    suspend fun insert(game: GameEntity): Long = gameDao.insert(game)

    suspend fun update(game: GameEntity) = gameDao.update(game)

    fun search(query: String): Flow<List<GameEntity>> = gameDao.search(query)

    suspend fun getDistinctGenres(): List<String> = gameDao.getDistinctGenres()

    suspend fun getDistinctGameModes(): List<String> = gameDao.getDistinctGameModes()

    fun observeHiddenByPlatformList(platformId: Long): Flow<List<GameListItem>> =
        gameDao.observeHiddenByPlatformList(platformId)

    fun observeHiddenList(): Flow<List<GameListItem>> = gameDao.observeHiddenList()

    fun observePlayableByPlatformList(platformId: Long): Flow<List<GameListItem>> =
        gameDao.observePlayableByPlatformList(platformId)

    fun observeFavoritesByPlatformList(platformId: Long): Flow<List<GameListItem>> =
        gameDao.observeFavoritesByPlatformList(platformId)

    fun observeByPlatformList(platformId: Long): Flow<List<GameListItem>> =
        gameDao.observeByPlatformList(platformId)

    fun observeAllList(): Flow<List<GameListItem>> = gameDao.observeAllList()

    fun observePlayableList(): Flow<List<GameListItem>> = gameDao.observePlayableList()

    fun observeFavoritesList(): Flow<List<GameListItem>> = gameDao.observeFavoritesList()

    suspend fun getNewlyAddedPlayable(
        threshold: Instant,
        limit: Int = 20
    ): List<GameEntity> = gameDao.getNewlyAddedPlayable(threshold, limit)

    suspend fun countByPlatform(platformId: Long): Int =
        gameDao.countByPlatform(platformId)

    suspend fun getByPlatformSorted(
        platformId: Long,
        limit: Int = 20
    ): List<GameEntity> = gameDao.getByPlatformSorted(platformId, limit)

    suspend fun getAllSortedByTitle(): List<GameEntity> =
        gameDao.getAllSortedByTitle()

    suspend fun getByPlatform(platformId: Long): List<GameEntity> =
        gameDao.getByPlatform(platformId)

    suspend fun countDownloadedByPlatform(platformId: Long): Int =
        gameDao.countDownloadedByPlatform(platformId)

    suspend fun countFavoritesByPlatform(platformId: Long): Int =
        gameDao.countFavoritesByPlatform(platformId)

    suspend fun updateSteamLauncher(
        gameId: Long,
        launcher: String?,
        setManually: Boolean
    ) = gameDao.updateSteamLauncher(gameId, launcher, setManually)

    suspend fun updateAchievementsFetchedAt(gameId: Long, timestamp: Long) =
        gameDao.updateAchievementsFetchedAt(gameId, timestamp)

    suspend fun updateAchievementCount(
        gameId: Long,
        count: Int,
        earnedCount: Int = 0
    ) = gameDao.updateAchievementCount(gameId, count, earnedCount)

    suspend fun updateFileSize(gameId: Long, sizeBytes: Long) =
        gameDao.updateFileSize(gameId, sizeBytes)

    suspend fun getFirstGameWithCover(): GameListItem? =
        gameDao.getFirstGameWithCover()

    suspend fun getRecentlyPlayedWithCovers(limit: Int = 10): List<GameListItem> =
        gameDao.getRecentlyPlayedWithCovers(limit)

    suspend fun getRecentlyPlayedOnPlatforms(
        platformSlugs: List<String>,
        limit: Int = 10
    ): List<GameListItem> = gameDao.getRecentlyPlayedOnPlatforms(platformSlugs, limit)

    suspend fun getCachedScreenshotPaths(gameId: Long): String? =
        gameDao.getCachedScreenshotPaths(gameId)

    suspend fun getScreenshotPaths(gameId: Long): String? =
        gameDao.getScreenshotPaths(gameId)

    suspend fun getByIgdbId(igdbId: Long): GameEntity? = gameDao.getByIgdbId(igdbId)

    suspend fun getBySteamAppId(steamAppId: Long): GameEntity? =
        gameDao.getBySteamAppId(steamAppId)

    fun searchForQuickMenu(query: String, limit: Int = 10): Flow<List<GameEntity>> =
        gameDao.searchForQuickMenu(query, limit)

    suspend fun getLocalGamesNeedingGradients(): List<com.nendo.argosy.data.local.dao.GradientExtractionCandidate> =
        gameDao.getLocalGamesNeedingGradients()

    suspend fun updateGradientColors(gameId: Long, json: String) =
        gameDao.updateGradientColors(gameId, json)

    suspend fun clearCoverPath(gameId: Long) = gameDao.clearCoverPath(gameId)

    suspend fun clearBackgroundPath(gameId: Long) = gameDao.clearBackgroundPath(gameId)

    suspend fun getGameFilesForGame(gameId: Long): List<com.nendo.argosy.data.local.entity.GameFileEntity> =
        gameFileDao.getFilesForGame(gameId)

    suspend fun getInstalledSteamGames(): List<GameEntity> =
        gameDao.getInstalledSteamGames()
}
