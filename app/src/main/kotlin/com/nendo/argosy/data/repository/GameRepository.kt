package com.nendo.argosy.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.SearchCandidate
import com.nendo.argosy.data.local.dao.UserRomsHiddenDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.platform.platformRomRoots
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.storage.StorageVolumeHealth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameRepository"
private const val COVER_RATIO_TOLERANCE = 0.001f

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
    private val userRomsHiddenDao: UserRomsHiddenDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val romMRepository: RomMRepository,
    private val overlayWriter: GameUserOverlayWriter,
    private val preferencesRepository: UserPreferencesRepository,
    private val fileAccessLayer: com.nendo.argosy.data.storage.FileAccessLayer,
    private val volumeHealth: StorageVolumeHealth,
    private val attributionRepository: StorageAttributionRepository
) {
    private val defaultDownloadDir: File by lazy {
        File(context.getExternalFilesDir(null), "downloads")
    }

    /**
     * Where this platform's roms are written.
     *
     * Prefer [downloadDirFor] when the caller holds a platform id. Slugs are not unique -
     * duplicate-slug rows are a state the schema allows and `getBySlug` takes the first of them -
     * so resolving by slug can hand one platform another's custom folder, and downloads then land
     * in the wrong library.
     */
    private suspend fun getDownloadDir(platformSlug: String): File =
        downloadDirFor(platformDao.getBySlug(platformSlug), platformSlug)

    private suspend fun downloadDirFor(platform: PlatformEntity?, platformSlug: String): File {
        platform?.customRomPath?.let { return File(it).also { dir -> dir.mkdirs() } }

        val base = preferencesRepository.userPreferences.first().romStoragePath
            ?.let { File(it) }
            ?: defaultDownloadDir
        return File(base, platformSlug).also { it.mkdirs() }
    }

    suspend fun getDownloadDirForPlatformId(platformId: Long): File = withContext(Dispatchers.IO) {
        val platform = platformDao.getById(platformId)
        downloadDirFor(platform, platform?.slug ?: return@withContext defaultDownloadDir)
    }

    private suspend fun candidateRootsFor(platform: PlatformEntity): List<File> {
        val base = preferencesRepository.userPreferences.first().romStoragePath
            ?.let { File(it) }
            ?: defaultDownloadDir
        return platformRomRoots(platform, base, platformDao.getAllPlatforms())
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

    private fun normalizeForMatch(name: String): String = FileNames.normalizeForMatch(name)

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
        return fileAccessLayer.exists(path)
    }

    /**
     * Whether every root this library can hold roms in currently answers as mounted and listable.
     * Any sweep that removes rows because their files were not found reads this once, before it
     * touches anything, so an unmounted card cannot be mistaken for an emptied library.
     */
    suspend fun romStorageVolumesReadable(): Boolean = withContext(Dispatchers.IO) {
        val roots = buildSet {
            add(defaultDownloadDir.absolutePath)
            preferencesRepository.userPreferences.first().romStoragePath?.let { add(it) }
            platformDao.getAllPlatforms().forEach { platform ->
                platform.customRomPath?.let { add(it) }
            }
        }.filter { it.startsWith("/") }
        volumeHealth.newProbe().allVolumesReadable(roots)
    }

    /**
     * Links games with no recorded path to entries already on disk, one platform at a time.
     *
     * Only empty paths are filled. A game that already points somewhere is never repointed by a
     * scan, so a library the user chose not to migrate keeps working.
     */
    private suspend fun claimInto(platform: PlatformEntity, games: List<GameEntity>): Int {
        val roots = candidateRootsFor(platform)
        val entries = mutableMapOf<ClaimCandidate, File>()
        for (root in roots) {
            val listed = root.listFiles() ?: continue
            for (entry in listed) {
                if (entry.isFile && entry.name.endsWith(".tmp")) continue
                if (entry.name.startsWith("._")) continue
                entries.putIfAbsent(ClaimCandidate(entry.name, entry.isDirectory), entry)
            }
        }
        if (entries.isEmpty()) return 0

        val targets = games.map { ClaimTarget(it.id, it.rommFileName, it.title) }
        val claims = claimLocalEntries(targets, entries.keys.toList())
        var discovered = 0

        for (game in games) {
            val candidate = claims[game.id] ?: continue
            val entry = entries[candidate] ?: continue
            val resolved = if (candidate.isDirectory) {
                findPrimaryRomInFolder(entry, platform.slug)
            } else {
                entry
            } ?: continue
            gameDao.updateLocalPath(game.id, resolved.absolutePath, GameSource.ROMM_SYNCED)
            discovered++
            Log.d(TAG, "Discovered: ${game.title} -> ${entry.name}")
        }
        return discovered
    }

    suspend fun discoverLocalFiles(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "discoverLocalFiles: storage not ready, skipping")
            return@withContext 0
        }

        val startTime = System.currentTimeMillis()
        val gamesWithoutPath = gameDao.getGamesWithRommIdButNoPath()
        if (gamesWithoutPath.isEmpty()) return@withContext 0

        val gamesByPlatformId = gamesWithoutPath.groupBy { it.platformId }
        var discovered = 0

        for ((platformId, games) in gamesByPlatformId) {
            val platform = platformDao.getById(platformId) ?: continue
            discovered += claimInto(platform, games)
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

        val probe = volumeHealth.newProbe()
        val startTime = System.currentTimeMillis()
        val gamesWithPaths = gameDao.getGamesWithLocalPathInfo()
        var invalidated = 0
        var withheld = 0
        for (info in gamesWithPaths) {
            val path = info.localPath ?: continue
            if (isGamePathValid(path, info.platformSlug)) continue
            if (!probe.isGenuinelyAbsent(path)) {
                withheld++
                continue
            }
            gameDao.clearLocalPath(info.id)
            invalidated++
            Log.d(TAG, "Invalidated game ${info.id}: path no longer valid ($path)")
        }
        var invalidatedFiles = 0
        for (row in gameFileDao.getAllWithLocalPath()) {
            val path = row.localPath ?: continue
            if (fileAccessLayer.exists(path)) continue
            if (!probe.isGenuinelyAbsent(path)) {
                withheld++
                continue
            }
            gameFileDao.clearLocalPath(row.id)
            invalidatedFiles++
            Log.d(TAG, "Invalidated file row ${row.id} (${row.fileName}): path no longer valid ($path)")
        }
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Validation complete: $invalidated of ${gamesWithPaths.size} games and $invalidatedFiles file rows invalidated in ${elapsed}ms")
        if (withheld > 0) {
            Log.w(TAG, "validateLocalFiles: kept $withheld pointers whose volume could not be read")
        }
        invalidated + invalidatedFiles
    }

    suspend fun repairFolderRomPointers(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "repairFolderRomPointers: storage not ready, skipping")
            return@withContext 0
        }

        val startTime = System.currentTimeMillis()
        val gamesWithPaths = gameDao.getGamesWithLocalPathInfo()
        var repaired = 0
        for (info in gamesWithPaths) {
            val path = info.localPath ?: continue
            val rebased = rebaseToFolderBase(info.id, info.platformSlug, info.source, path) ?: continue
            repaired++
            Log.i(TAG, "Repaired rom pointer for game ${info.id}: $path -> $rebased")
        }
        val elapsed = System.currentTimeMillis() - startTime
        if (repaired > 0) Log.i(TAG, "Repaired $repaired rom pointers in ${elapsed}ms")
        repaired
    }

    suspend fun repairUnnecessaryM3uPointers(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "repairUnnecessaryM3uPointers: storage not ready, skipping")
            return@withContext 0
        }

        var repaired = 0
        for (info in gameDao.getGamesWithLocalPathInfo()) {
            val path = info.localPath ?: continue
            val m3u = File(path)
            if (!m3u.extension.equals("m3u", ignoreCase = true)) continue
            if (M3uManager.isMultiDiscContainer(m3u)) continue

            val replacement = M3uManager.parseFirstDisc(m3u)
            if (replacement == null) {
                Log.w(TAG, "Unnecessary m3u for game ${info.id} ($path) but no launch target found")
                continue
            }

            gameDao.updateLocalPath(info.id, replacement.absolutePath, info.source)
            gameDao.updateM3uPath(info.id, null)
            if (m3u.absolutePath != replacement.absolutePath) m3u.delete()
            repaired++
            Log.i(TAG, "Repaired unnecessary m3u for game ${info.id} (${info.platformSlug}): $path -> ${replacement.absolutePath}")
        }
        if (repaired > 0) Log.i(TAG, "Repaired $repaired unnecessary m3u pointers")
        repaired
    }

    private suspend fun rebaseToFolderBase(
        gameId: Long,
        platformSlug: String,
        source: GameSource,
        localPath: String
    ): String? {
        val file = File(localPath)
        val gameFolder = when {
            file.isDirectory -> file
            platformSlug !in VariantCategory.VARIANT_EXCLUDED_PLATFORMS &&
                file.parentFile?.name?.lowercase() in VariantCategory.CATEGORY_FOLDER_NAMES ->
                file.parentFile?.parentFile
            else -> return null
        } ?: return null
        val base = findPrimaryRomInFolder(gameFolder, platformSlug) ?: return null
        if (base.absolutePath == localPath) return null
        gameDao.updateLocalPath(gameId, base.absolutePath, source)
        return base.absolutePath
    }

    suspend fun repairVariantFilePointers(): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) {
            Log.w(TAG, "repairVariantFilePointers: storage not ready, skipping")
            return@withContext 0
        }

        val startTime = System.currentTimeMillis()
        val missing = gameFileDao.getMissingFilesWithGameInfo()
        if (missing.isEmpty()) return@withContext 0
        Log.d(TAG, "repairVariantFilePointers: checking ${missing.size} entries without localPath")

        var repaired = 0
        for ((platformId, entries) in missing.groupBy { it.platformId }) {
            val platformSlug = entries.first().platformSlug
            val platformDir = getDownloadDirForPlatformId(platformId)
            if (!platformDir.isDirectory) continue
            val fileIndex by lazy { buildPlatformFileIndex(platformDir) }
            val folderExists = HashMap<String, Boolean>()

            for (entry in entries) {
                val folderCandidates = buildList {
                    entry.rommFileName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    add(entry.gameTitle)
                }.distinct()
                val categoryCandidates =
                    (listOf(entry.category) + ZipExtractor.addonFolderNames(entry.category)).distinct()

                val found = folderCandidates.firstNotNullOfOrNull { folderName ->
                    val gameFolder = File(platformDir, folderName)
                    val exists = folderExists.getOrPut(folderName) { gameFolder.isDirectory }
                    if (!exists) return@firstNotNullOfOrNull null
                    categoryCandidates.firstNotNullOfOrNull { category ->
                        val categoryFolder = File(gameFolder, category)
                        val candidate = File(categoryFolder, entry.fileName)
                        candidate.takeIf { it.isFile }
                    }
                } ?: fileIndex[entry.fileName]
                ?: continue

                gameFileDao.updateLocalPath(entry.fileId, found.absolutePath, Instant.now())
                repaired++
                Log.i(TAG, "Repaired variant pointer for file ${entry.fileId} (${entry.fileName}) -> ${found.absolutePath}")
            }
        }
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "repairVariantFilePointers: $repaired repaired in ${elapsed}ms")
        repaired
    }

    private fun buildPlatformFileIndex(platformDir: File): Map<String, File> {
        val index = HashMap<String, File>()
        platformDir.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && !it.name.startsWith("._") }
            .forEach { file -> if (file.name !in index) index[file.name] = file }
        return index
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
                if (!File(game.localPath).isDirectory) {
                    rebaseToFolderBase(game.id, game.platformSlug, game.source, game.localPath)
                }
                return@withContext true
            }
            if (!volumeHealth.newProbe().isGenuinelyAbsent(game.localPath)) {
                Log.w(TAG, "Kept path for ${game.title}: volume could not be read")
                return@withContext true
            }
            gameDao.clearLocalPath(gameId)
            Log.d(TAG, "Cleared invalid path for: ${game.title}")
        }

        if (game.rommId == null) return@withContext false

        val platform = platformDao.getById(game.platformId) ?: return@withContext false
        claimInto(platform, listOf(game)) > 0
    }

    suspend fun getDownloadedGamesSize(): Long = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPathInfo().sumOf { info ->
            info.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.length() else 0L
            } ?: 0L
        }
    }

    suspend fun getDownloadedGamesCount(): Int = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPathIds().size
    }

    suspend fun countBoxArtCapableGames(): Int = withContext(Dispatchers.IO) {
        gameDao.countBoxArtCapable()
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

    suspend fun getGamesWithLocalPaths(): List<GameEntity> = withContext(Dispatchers.IO) {
        gameDao.getByIdsChunked(gameDao.getGamesWithLocalPathIds())
    }

    suspend fun getGamesWithLocalPathInfo() = withContext(Dispatchers.IO) {
        gameDao.getGamesWithLocalPathInfo()
    }

    suspend fun getGamesWithLocalPathsForPlatform(platformId: Long): List<GameEntity> = withContext(Dispatchers.IO) {
        val ids = gameDao.getGamesWithLocalPathInfo()
            .filter { it.platformId == platformId }
            .map { it.id }
        gameDao.getByIdsChunked(ids)
    }


    suspend fun getVariantsForGame(gameId: Long): List<GameFileEntity> = withContext(Dispatchers.IO) {
        gameFileDao.getVariantsForGame(gameId)
    }

    suspend fun setActiveVariant(gameId: Long, fileId: Long?) = withContext(Dispatchers.IO) {
        gameDao.updateActiveVariantFileId(gameId, fileId)
    }

    suspend fun updateLocalPath(gameId: Long, newPath: String) = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext
        gameDao.updateLocalPath(gameId, newPath, game.source)
    }

    suspend fun clearLocalPath(gameId: Long) = withContext(Dispatchers.IO) {
        gameDao.clearLocalPath(gameId)
    }

    /**
     * Whether [path] is missing from a volume that answers as mounted and listable. Callers
     * outside this repository use it so an unreadable card never reads as deleted content.
     */
    suspend fun isPathGenuinelyAbsent(path: String): Boolean = withContext(Dispatchers.IO) {
        volumeHealth.newProbe().isGenuinelyAbsent(path)
    }

    /**
     * Drops the pointer only when [path] is missing from a volume that can actually be read.
     * Returns whether it was dropped, so callers that count "missing" can tell a file the user
     * removed from one sitting on a card that is not answering.
     */
    suspend fun clearLocalPathIfGenuinelyAbsent(gameId: Long, path: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!volumeHealth.newProbe().isGenuinelyAbsent(path)) {
                Log.w(TAG, "Kept pointer for game $gameId: volume could not be read ($path)")
                return@withContext false
            }
            gameDao.clearLocalPath(gameId)
            true
        }

    suspend fun getPlatformBreakdowns(): List<PlatformStats> = withContext(Dispatchers.IO) {
        val platforms = platformDao.observeAllPlatforms().first()
        val storageInfo = gameDao.getAllStorageInfo(overlayWriter.activeOwnerId())
        val byPlatform = storageInfo.groupBy { it.platformId }

        platforms.mapNotNull { platform ->
            val platformGames = byPlatform[platform.id] ?: return@mapNotNull null
            if (platformGames.isEmpty()) return@mapNotNull null

            val downloadedGames = platformGames.filter { it.localPath != null }
            val downloadedSize = downloadedGames.sumOf { info ->
                info.localPath?.let { path ->
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

        val probe = volumeHealth.newProbe()
        val games = gameDao.getGamesWithLocalPathByPlatform(platformId)
        var invalidated = 0
        var withheld = 0
        for (game in games) {
            val path = game.localPath ?: continue
            if (isGamePathValid(path, game.platformSlug)) continue
            val resolved = resolveFileFallback(path, game.platformSlug)
            if (resolved != null) {
                gameDao.updateLocalPath(game.id, resolved, game.source)
                Log.d(TAG, "Resolved (fallback): ${game.title} -> $resolved")
                continue
            }
            if (!probe.isGenuinelyAbsent(path)) {
                withheld++
                continue
            }
            gameDao.clearLocalPath(game.id)
            invalidated++
            Log.d(TAG, "Invalidated (platform): ${game.title}")
        }
        if (withheld > 0) {
            Log.w(TAG, "validateLocalFilesForPlatform: kept $withheld pointers whose volume could not be read")
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
        attributionRepository.markDirty(StorageCategory.GAMES)
        deleted
    }

    suspend fun discoverLocalFilesForPlatform(platformId: Long): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) return@withContext 0

        val games = gameDao.getGamesWithRommIdButNoPathByPlatform(platformId)
        if (games.isEmpty()) return@withContext 0

        val platform = platformDao.getById(platformId) ?: return@withContext 0
        claimInto(platform, games)
    }

    suspend fun validateDiscLocalFiles(platformId: Long): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) return@withContext 0

        val probe = volumeHealth.newProbe()
        val discs = gameDiscDao.getDiscsWithLocalPathByPlatform(platformId)
        var invalidated = 0
        for (disc in discs) {
            val path = disc.localPath ?: continue
            if (!probe.isGenuinelyAbsent(path)) continue
            gameDiscDao.clearLocalPath(disc.id)
            invalidated++
        }
        invalidated
    }

    suspend fun validateFileLocalFiles(platformId: Long): Int = withContext(Dispatchers.IO) {
        if (!isStorageReady()) return@withContext 0

        val probe = volumeHealth.newProbe()
        val files = gameFileDao.getFilesWithLocalPathByPlatform(platformId)
        var invalidated = 0
        for (file in files) {
            val path = file.localPath ?: continue
            if (!probe.isGenuinelyAbsent(path)) continue
            gameFileDao.clearLocalPath(file.id)
            invalidated++
        }
        invalidated
    }

    suspend fun ensureImagePathValid(gameId: Long): GameEntity? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        val probe = volumeHealth.newProbe()
        var changed = false

        if (game.coverPath?.startsWith("/") == true && probe.isGenuinelyAbsent(game.coverPath)) {
            gameDao.clearCoverPath(gameId)
            changed = true
        }
        if (game.backgroundPath?.startsWith("/") == true && probe.isGenuinelyAbsent(game.backgroundPath)) {
            gameDao.clearBackgroundPath(gameId)
            changed = true
        }

        if (changed) gameDao.getById(gameId) else game
    }

    /**
     * The account every hidden-aware query below is run for. Resolved once per call and passed
     * down, never re-read partway through an operation.
     */
    private suspend fun hiddenOwnerId(): Long? = overlayWriter.activeOwnerId()

    suspend fun isGameHidden(gameId: Long): Boolean =
        userRomsHiddenDao.isHidden(hiddenOwnerId(), gameId)

    suspend fun getHiddenGameIds(): Set<Long> =
        userRomsHiddenDao.hiddenGameIds(hiddenOwnerId()).toSet()

    suspend fun getById(id: Long): GameEntity? = gameDao.getById(id)

    fun observeById(id: Long): Flow<GameEntity?> = gameDao.observeById(id)

    suspend fun getPlayedGames(): List<GameEntity> = gameDao.getPlayedGames(hiddenOwnerId())

    suspend fun getRecentlyPlayed(limit: Int = 20): List<GameEntity> =
        gameDao.getRecentlyPlayed(hiddenOwnerId(), limit)

    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<GameEntity>> = flow {
        emitAll(gameDao.observeRecentlyPlayed(hiddenOwnerId(), limit))
    }

    suspend fun getFavorites(): List<GameEntity> =
        hydrateByIds(gameDao.getFavoriteIds(hiddenOwnerId()))

    suspend fun getRandomGame(): GameEntity? = gameDao.getRandomGame(hiddenOwnerId())

    suspend fun getSearchCandidates(): List<SearchCandidate> =
        gameDao.getSearchCandidates(hiddenOwnerId())

    suspend fun getByIds(ids: List<Long>): List<GameEntity> = gameDao.getByIds(ids)

    private suspend fun hydrateByIds(ids: List<Long>): List<GameEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = gameDao.getByIdsChunked(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun updateUserRating(gameId: Long, rating: Int) =
        overlayWriter.updateUserRating(gameId, rating)

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int) =
        overlayWriter.updateUserDifficulty(gameId, difficulty)

    suspend fun updateStatus(gameId: Long, status: String?) =
        overlayWriter.updateStatus(gameId, status)

    suspend fun updateFavorite(gameId: Long, favorite: Boolean) =
        overlayWriter.updateFavorite(gameId, favorite)

    suspend fun updateFavoriteWithSync(gameId: Long, favorite: Boolean) {
        val rommId = gameDao.getById(gameId)?.rommId
        if (rommId != null) {
            romMRepository.toggleFavoriteWithSync(gameId, rommId, favorite)
        } else {
            overlayWriter.updateFavorite(gameId, favorite)
        }
    }

    /**
     * Hides or shows a rom for the signed-in account. A rom the server knows about also queues
     * the change onto its `rom_user` block; a purely local rom stops at the join table.
     */
    suspend fun updateHidden(gameId: Long, hidden: Boolean) {
        val rommId = gameDao.getById(gameId)?.rommId
        if (rommId != null) {
            romMRepository.updateHidden(gameId, hidden)
        } else {
            overlayWriter.setHidden(gameId, hidden)
        }
    }

    suspend fun getBySource(source: GameSource): List<GameEntity> =
        gameDao.getBySource(source)

    suspend fun getByPackageName(packageName: String): GameEntity? =
        gameDao.getByPackageName(packageName)

    suspend fun delete(game: GameEntity) = gameDao.delete(game)

    suspend fun delete(gameId: Long) = gameDao.delete(gameId)

    suspend fun insert(game: GameEntity): Long = gameDao.insert(game)

    suspend fun update(game: GameEntity) = gameDao.update(game)

    fun search(query: String): Flow<List<GameEntity>> = flow {
        emitAll(
            gameDao.search(
                com.nendo.argosy.util.SearchNormalizer.normalize(query),
                hiddenOwnerId()
            )
        )
    }

    suspend fun getDistinctGenres(): List<String> = gameDao.getDistinctGenres(hiddenOwnerId())

    suspend fun getDistinctGameModes(): List<String> = gameDao.getDistinctGameModes(hiddenOwnerId())

    fun observeHiddenByPlatformList(platformId: Long): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observeHiddenByPlatformList(platformId, hiddenOwnerId()))
    }

    fun observeHiddenList(): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observeHiddenList(hiddenOwnerId()))
    }

    fun observePlayableByPlatformList(platformId: Long): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observePlayableByPlatformList(platformId, hiddenOwnerId()))
    }

    fun observeFavoritesByPlatformList(platformId: Long): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observeFavoritesByPlatformList(platformId, hiddenOwnerId()))
    }

    fun observeByPlatformList(platformId: Long): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observeByPlatformList(platformId, hiddenOwnerId()))
    }

    fun observeAllList(): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observeAllList(hiddenOwnerId()))
    }

    fun observePlayableList(): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observePlayableList(hiddenOwnerId()))
    }

    fun observeFavoritesList(): Flow<List<GameListItem>> = flow {
        emitAll(gameDao.observeFavoritesList(hiddenOwnerId()))
    }

    suspend fun getNewlyAddedPlayable(
        threshold: Instant,
        limit: Int = 20
    ): List<GameEntity> = gameDao.getNewlyAddedPlayable(threshold, hiddenOwnerId(), limit)

    suspend fun countByPlatform(platformId: Long): Int =
        gameDao.countByPlatform(platformId, hiddenOwnerId())

    /**
     * Game counts for every platform at once, keyed by platform id. A platform with nothing in it is
     * absent from the map rather than present as zero.
     */
    suspend fun countsByPlatform(): Map<Long, Int> =
        gameDao.countsByPlatform(hiddenOwnerId()).associate { it.platformId to it.gameCount }

    suspend fun getByPlatformSorted(
        platformId: Long,
        limit: Int = 20
    ): List<GameEntity> = gameDao.getByPlatformSorted(platformId, hiddenOwnerId(), limit)

    suspend fun getAllSortedByTitle(): List<GameEntity> =
        hydrateByIds(gameDao.getAllSortedByTitleIds(hiddenOwnerId()))

    suspend fun getHiddenSortedByTitle(): List<GameEntity> =
        hydrateByIds(gameDao.getHiddenSortedByTitleIds(hiddenOwnerId()))

    suspend fun getByPlatform(platformId: Long): List<GameEntity> =
        gameDao.getByPlatform(platformId, hiddenOwnerId())

    suspend fun getHiddenByPlatform(platformId: Long): List<GameEntity> =
        gameDao.getHiddenByPlatform(platformId, hiddenOwnerId())

    suspend fun countDownloadedByPlatform(platformId: Long): Int =
        gameDao.countDownloadedByPlatform(platformId)

    suspend fun countFavoritesByPlatform(platformId: Long): Int =
        gameDao.countFavoritesByPlatform(platformId, hiddenOwnerId())

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
    ) = overlayWriter.updateAchievementCount(gameId, count, earnedCount)

    suspend fun updateFileSize(gameId: Long, sizeBytes: Long) =
        gameDao.updateFileSize(gameId, sizeBytes)

    suspend fun getFirstGameWithCover(): GameListItem? =
        gameDao.getFirstGameWithCover(hiddenOwnerId())

    suspend fun getRecentlyPlayedWithCovers(limit: Int = 10): List<GameListItem> =
        gameDao.getRecentlyPlayedWithCovers(hiddenOwnerId(), limit)

    suspend fun getRecentlyPlayedOnPlatforms(
        platformSlugs: List<String>,
        limit: Int = 10
    ): List<GameListItem> =
        gameDao.getRecentlyPlayedOnPlatforms(platformSlugs, hiddenOwnerId(), limit)

    suspend fun getCachedScreenshotPaths(gameId: Long): String? =
        gameDao.getCachedScreenshotPaths(gameId)

    suspend fun getScreenshotPaths(gameId: Long): String? =
        gameDao.getScreenshotPaths(gameId)

    suspend fun getByIgdbId(igdbId: Long): GameEntity? = gameDao.getByIgdbId(igdbId)

    suspend fun getBySteamAppId(steamAppId: Long): GameEntity? =
        gameDao.getBySteamAppId(steamAppId)

    fun searchForQuickMenu(query: String, limit: Int = 10): Flow<List<GameEntity>> = flow {
        emitAll(
            gameDao.searchForQuickMenu(
                com.nendo.argosy.util.SearchNormalizer.normalize(query),
                hiddenOwnerId(),
                limit
            )
        )
    }

    /**
     * Writes only when the shape is new or has changed, so drawing a cover does not queue a write
     * on every pass over a row.
     */
    suspend fun recordCoverAspectRatio(gameId: Long, ratio: Float) {
        if (!ratio.isFinite() || ratio <= 0f) return
        val stored = gameDao.getCoverAspectRatio(gameId)
        if (stored != null && kotlin.math.abs(stored - ratio) < COVER_RATIO_TOLERANCE) return
        gameDao.updateCoverAspectRatio(gameId, ratio)
    }

    fun searchInstalled(query: String, limit: Int): Flow<List<GameEntity>> = flow {
        emitAll(
            gameDao.searchInstalled(
                com.nendo.argosy.util.SearchNormalizer.normalize(query),
                hiddenOwnerId(),
                limit
            )
        )
    }

    suspend fun getLocalGamesNeedingGradients(): List<com.nendo.argosy.data.local.dao.GradientExtractionCandidate> =
        gameDao.getLocalGamesNeedingGradients(hiddenOwnerId())

    suspend fun updateGradientColors(gameId: Long, json: String) =
        gameDao.updateGradientColors(gameId, json)

    suspend fun clearCoverPath(gameId: Long) = gameDao.clearCoverPath(gameId)

    suspend fun clearBackgroundPath(gameId: Long) = gameDao.clearBackgroundPath(gameId)

    suspend fun getGameFilesForGame(gameId: Long): List<com.nendo.argosy.data.local.entity.GameFileEntity> =
        gameFileDao.getFilesForGame(gameId)

    suspend fun getInstalledSteamGames(): List<GameEntity> =
        gameDao.getInstalledSteamGames()
}
