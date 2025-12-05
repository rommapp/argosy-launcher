package com.nendo.argosy.data.scanner

import android.net.Uri
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformDef
import com.nendo.argosy.data.platform.PlatformDefinitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomScanner"

data class ScanProgress(
    val isScanning: Boolean = false,
    val currentPath: String = "",
    val filesScanned: Int = 0,
    val gamesFound: Int = 0,
    val gamesSkipped: Int = 0,
    val platformsFound: Set<String> = emptySet()
)

data class ScanResult(
    val gamesAdded: Int,
    val gamesUpdated: Int,
    val gamesSkipped: Int,
    val platformsWithGames: Set<String>,
    val errors: List<String>
)

@Singleton
class RomScanner @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao
) {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    suspend fun initializePlatforms() {
        withContext(Dispatchers.IO) {
            platformDao.insertAll(PlatformDefinitions.toEntities())
        }
    }

    suspend fun scanDirectory(path: String): ScanResult = withContext(Dispatchers.IO) {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext ScanResult(0, 0, 0, emptySet(), listOf("Invalid directory: $path"))
        }

        _progress.value = ScanProgress(isScanning = true)
        Log.d(TAG, "Starting scan of $path")

        val errors = mutableListOf<String>()
        var gamesAdded = 0
        var gamesUpdated = 0
        var gamesSkipped = 0
        val platformsWithGames = mutableSetOf<String>()

        try {
            scanRecursive(directory, errors) { sortTitle, platformId, localPath ->
                val existing = gameDao.getBySortTitleAndPlatform(sortTitle, platformId)
                if (existing != null) {
                    if (existing.localPath == null) {
                        gameDao.updateLocalPath(existing.id, localPath, GameSource.ROMM_SYNCED)
                        gamesUpdated++
                        platformsWithGames.add(platformId)
                        Log.d(TAG, "Marked as installed: ${existing.title}")
                    } else {
                        gamesUpdated++
                    }
                } else {
                    gamesSkipped++
                }
                _progress.value = _progress.value.copy(
                    gamesFound = gamesUpdated,
                    gamesSkipped = gamesSkipped,
                    platformsFound = platformsWithGames.toSet()
                )
            }

            platformsWithGames.forEach { platformId ->
                val count = gameDao.countByPlatform(platformId)
                platformDao.updateGameCount(platformId, count)
            }

            Log.d(TAG, "Scan complete: updated=$gamesUpdated, skipped=$gamesSkipped")
        } finally {
            _progress.value = ScanProgress(isScanning = false)
        }

        ScanResult(gamesAdded, gamesUpdated, gamesSkipped, platformsWithGames, errors)
    }

    suspend fun scanUri(uri: Uri, contentResolver: android.content.ContentResolver): ScanResult {
        return withContext(Dispatchers.IO) {
            ScanResult(0, 0, 0, emptySet(), listOf("URI scanning not yet implemented"))
        }
    }

    private suspend fun scanRecursive(
        directory: File,
        errors: MutableList<String>,
        onRomFound: suspend (sortTitle: String, platformId: String, localPath: String) -> Unit
    ) {
        val files = directory.listFiles() ?: return

        for (file in files) {
            _progress.value = _progress.value.copy(
                currentPath = file.absolutePath,
                filesScanned = _progress.value.filesScanned + 1
            )

            when {
                file.isDirectory -> scanRecursive(file, errors, onRomFound)
                file.isFile -> tryParseRom(file, directory)?.let { (sortTitle, platformId, localPath) ->
                    try {
                        onRomFound(sortTitle, platformId, localPath)
                    } catch (e: Exception) {
                        errors.add("Failed to process ${file.name}: ${e.message}")
                    }
                }
            }
        }
    }

    private fun tryParseRom(file: File, parentDir: File): Triple<String, String, String>? {
        val extension = file.extension.lowercase()
        if (extension.isEmpty()) return null

        val platforms = PlatformDefinitions.getPlatformsForExtension(extension)
        if (platforms.isEmpty()) return null

        val platform = resolvePlatform(platforms, parentDir, file)
        val title = cleanRomTitle(file.nameWithoutExtension)
        val sortTitle = createSortTitle(title)

        return Triple(sortTitle, platform.id, file.absolutePath)
    }

    private fun resolvePlatform(
        candidates: List<PlatformDef>,
        parentDir: File,
        file: File
    ): PlatformDef {
        if (candidates.size == 1) return candidates.first()

        val dirName = parentDir.name.lowercase()
        val pathLower = file.absolutePath.lowercase()

        for (candidate in candidates) {
            val idLower = candidate.id.lowercase()
            val nameLower = candidate.name.lowercase()
            val shortLower = candidate.shortName.lowercase()

            if (dirName.contains(idLower) ||
                dirName.contains(shortLower) ||
                pathLower.contains("/$idLower/") ||
                pathLower.contains("/${shortLower.lowercase()}/")
            ) {
                return candidate
            }
        }

        return candidates.minByOrNull { it.sortOrder } ?: candidates.first()
    }

    private fun cleanRomTitle(filename: String): String {
        var title = filename

        title = title.replace(Regex("\\s*\\([^)]*\\)"), "")
        title = title.replace(Regex("\\s*\\[[^]]*\\]"), "")

        title = title
            .replace("_", " ")
            .replace("  ", " ")
            .trim()

        return title.ifEmpty { filename }
    }

    private fun createSortTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.startsWith("the ") -> title.drop(4)
            lower.startsWith("a ") -> title.drop(2)
            lower.startsWith("an ") -> title.drop(3)
            else -> title
        }.lowercase()
    }
}
