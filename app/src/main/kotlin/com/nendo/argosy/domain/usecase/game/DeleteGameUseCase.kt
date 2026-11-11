package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.OrphanedFileDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.OrphanedFileEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.util.DiscSheets
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "DeleteGameUseCase"

class DeleteGameUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameRepository: GameRepository,
    private val downloadManager: dagger.Lazy<DownloadManager>,
    private val gameFileDao: GameFileDao,
    private val gameDiscDao: GameDiscDao,
    private val saveCacheManager: SaveCacheManager,
    private val stateCacheManager: com.nendo.argosy.data.repository.StateCacheManager,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val orphanedFileDao: OrphanedFileDao,
    private val steamRepository: SteamRepository,
    private val payloadCodec: SyncPayloadCodec,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val attributionRepository: StorageAttributionRepository
) {
    suspend operator fun invoke(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false

        if (game.source == GameSource.STEAM) {
            return deleteSteamGame(game.steamAppId)
        }

        val path = game.localPath ?: return false
        val platformFolder = gameRepository.getDownloadDirForPlatformId(game.platformId)

        gameRepository.clearLocalPath(gameId)
        downloadManager.get().discardForGame(gameId)
        val musicDirPrefix = musicDirectoryManager.resolveMusicDir().absolutePath + File.separator
        val sharedAddonPaths = gameFileDao.getFilesForGame(gameId)
            .mapNotNull { it.localPath }
            .filterNot { it.startsWith(musicDirPrefix) }
            .filter { File(it).parentFile?.name?.lowercase() == ZipExtractor.EXTCONTENT_FOLDER }
        gameFileDao.clearLocalPathsByGameIdExcludingPrefix(gameId, musicDirPrefix)
        val discPaths = gameDiscDao.getDiscsForGame(gameId).mapNotNull { disc ->
            disc.localPath?.also { gameDiscDao.clearLocalPath(disc.id) }
        }

        val keptSaves = saveCacheManager.deleteServerHeldCachesForGame(gameId)
        val keptStates = stateCacheManager.deleteServerHeldStatesForGame(gameId)
        saveSyncDao.deleteByGame(gameId)
        deleteQueuedScreenshotFiles(gameId)
        pendingSyncQueueDao.deleteByGameId(gameId)
        if (keptSaves + keptStates > 0) {
            Logger.info(TAG, "Kept $keptSaves saves and $keptStates states with no server copy for game $gameId")
        }

        withContext(Dispatchers.IO) {
            deleteRecordedFiles(sharedAddonPaths, platformFolder)
            deleteRecordedFiles(discPaths, platformFolder)
            try {
                val file = File(path)
                if (!file.exists()) return@withContext

                val deleted = if (file.isDirectory) {
                    if (!FileNames.isWithin(platformFolder, file)) {
                        file.deleteRecursively()
                    } else {
                        Logger.warn(TAG, "Refusing to delete folder $path: it holds the platform folder ${platformFolder.path}")
                        false
                    }
                } else {
                    val parent = file.parentFile
                    if (parent != null && FileNames.isInside(parent, platformFolder)) {
                        parent.deleteRecursively()
                    } else {
                        deleteDiscCompanions(file, game.platformSlug)
                        file.delete()
                    }
                }

                if (!deleted) {
                    Logger.warn(TAG, "Failed to delete $path, adding to orphan index")
                    orphanedFileDao.insert(OrphanedFileEntity(path = path))
                }
            } catch (e: Exception) {
                Logger.warn(TAG, "Error deleting file $path: ${e.message}")
                orphanedFileDao.insert(OrphanedFileEntity(path = path))
            }
        }

        attributionRepository.markDirty(StorageCategory.GAMES)
        attributionRepository.markDirty(StorageCategory.SAVE_STATE_CACHE)
        Logger.debug(TAG, "Deleted local file and server-held saves and states for game $gameId")
        return true
    }

    private fun deleteRecordedFiles(paths: List<String>, platformFolder: File) {
        if (paths.isEmpty()) return
        for (path in paths) {
            val file = File(path)
            val parent = file.parentFile ?: continue
            if (!runCatching { FileNames.isWithin(parent, platformFolder) }.getOrDefault(false)) continue
            if (file.isFile && !file.delete()) {
                Logger.warn(TAG, "Failed to delete ${file.name}")
            }
        }
    }

    private suspend fun deleteDiscCompanions(launchFile: File, platformSlug: String) {
        if (!DiscSheets.isLoader(launchFile.name)) return
        val romExtensions = PlatformDefinitions.definitionFor(platformSlug)?.extensions ?: return
        for (companion in DiscSheets.companions(launchFile, romExtensions)) {
            if (!companion.delete()) {
                Logger.warn(TAG, "Failed to delete disc track ${companion.name}, adding to orphan index")
                orphanedFileDao.insert(OrphanedFileEntity(path = companion.absolutePath))
            }
        }
    }

    private suspend fun deleteQueuedScreenshotFiles(gameId: Long) {
        val rows = pendingSyncQueueDao.getByGameId(gameId)
            .filter { it.syncType == SyncType.SCREENSHOT }
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            rows.forEach { row ->
                payloadCodec.decodeScreenshot(row.payloadJson)?.let { File(it.localPath).delete() }
            }
        }
    }

    /**
     * Drops a Steam game from the library, files included. Deleting a download is
     * [invoke]; this is the only path that discards the row.
     */
    suspend fun removeFromLibrary(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false
        if (game.source != GameSource.STEAM) return false
        val steamAppId = game.steamAppId ?: run {
            Logger.warn(TAG, "Cannot remove Steam game without steamAppId")
            return false
        }
        return when (val result = steamRepository.removeGame(steamAppId)) {
            is SteamResult.Success -> {
                Logger.debug(TAG, "Removed Steam game $steamAppId from library")
                true
            }
            is SteamResult.Error -> {
                Logger.warn(TAG, "Failed to remove Steam game: ${result.message}")
                false
            }
        }
    }

    private suspend fun deleteSteamGame(steamAppId: Long?): Boolean {
        if (steamAppId == null) {
            Logger.warn(TAG, "Cannot uninstall Steam game without steamAppId")
            return false
        }
        val result = steamRepository.uninstallGame(steamAppId)
        return when (result) {
            is SteamResult.Success -> {
                Logger.debug(TAG, "Uninstalled Steam game $steamAppId")
                true
            }
            is SteamResult.Error -> {
                Logger.warn(TAG, "Failed to uninstall Steam game: ${result.message}")
                false
            }
        }
    }
}
