package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.platformRomRoots
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BaseRomFileResolver"

/**
 * Resolves the file that is the game itself from the path the library recorded for it.
 *
 * A game's local path can land on an update, a dlc or a file inside a content subfolder, and on
 * title id platforms the largest file in the game's folder is the game. Launching and title id
 * extraction share this so they can never read different files for the same game.
 */
@Singleton
class BaseRomFileResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * An update or DLC file is unbootable alone, so redirect to the base rom and persist it.
     *
     * Title id platforms always re-elect the base, other platforms only when the target is
     * provably update/dlc content, preferring an m3u so multi-disc folders keep their playlist.
     */
    suspend fun resolve(game: GameEntity, romFile: File): File {
        game.activeVariantFileId?.let { fileId ->
            val chosen = gameFileDao.getById(fileId)
            val chosenPath = chosen?.localPath
            if (chosen?.versionGroup != null && chosenPath != null && File(chosenPath).exists()) {
                if (chosenPath != romFile.absolutePath) {
                    Logger.info(TAG, "honoring active version ${chosen.fileName} for ${game.title}")
                    gameDao.updateLocalPath(game.id, chosenPath, game.source)
                }
                return File(chosenPath)
            }
        }
        val excluded = game.platformSlug in VariantCategory.TITLE_ID_PLATFORMS
        val parent = romFile.parentFile ?: return romFile
        val inContentSubfolder = parent.name.lowercase() in ZipExtractor.ADDON_FOLDERS
        if (!excluded && !inContentSubfolder && !isUpdateOrDlc(romFile.name)) return romFile
        val gameFolder = if (inContentSubfolder) {
            parent.parentFile ?: return romFile
        } else {
            parent
        }
        if (isPlatformRoot(gameFolder, game.platformSlug)) return romFile
        val candidates = (gameFolder.listFiles()?.filter { it.isFile } ?: return romFile)
            .filterNot { isUpdateOrDlc(it.name) }
        val base = if (excluded) {
            candidates.maxByOrNull { it.length() }
        } else {
            candidates.firstOrNull { it.extension.equals("m3u", ignoreCase = true) }
                ?: candidates.maxByOrNull { it.length() }
        } ?: return romFile
        if (base.absolutePath == romFile.absolutePath) return romFile
        Logger.info(TAG, "redirecting ${romFile.name} -> ${base.name} for ${game.title}")
        gameDao.updateLocalPath(game.id, base.absolutePath, game.source)
        return base
    }

    private fun isUpdateOrDlc(fileName: String): Boolean {
        val stem = fileName.substringBeforeLast('.')
        return UPDATE_DLC_TAG.containsMatchIn(stem) || UPDATE_DLC_SUFFIX.containsMatchIn(stem)
    }

    /**
     * True when [dir] is a directory roms sit directly inside rather than a game's own folder.
     *
     * Checked against every root discovery is allowed to link from, not just the download
     * destination: a rom found in one of the other roots would otherwise look like it lived in a
     * game folder, and the folder logic would repoint the game at the platform directory's
     * largest file.
     */
    private suspend fun isPlatformRoot(dir: File, platformSlug: String): Boolean {
        val target = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
        return platformRootsFor(platformSlug).any { root ->
            runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile) == target
        }
    }

    private suspend fun platformRootsFor(platformSlug: String): List<File> {
        val platform = platformDao.getAllBySlug(platformSlug).singleOrNull()
        val base = userPreferencesRepository.userPreferences.first().romStoragePath
            ?.let { File(it) }
            ?: File(context.getExternalFilesDir(null), "downloads")
        if (platform == null) return listOf(File(base, platformSlug))
        return platformRomRoots(platform, base, platformDao.getAllPlatforms())
    }

    private companion object {
        val UPDATE_DLC_SUFFIX = Regex("(?i)[ _-]+(update|dlc)([ _-]?\\d+)?$")
        val UPDATE_DLC_TAG = Regex("(?i)\\[(upd|update|dlc)]")
    }
}
