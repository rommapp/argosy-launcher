package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.music.BgmPlaylistRepository
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.DownloadDefaults
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.model.FilePickerRow
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.storage.StorageVolumeHealth
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private fun pathOnDisk(path: String?): Boolean = path != null && File(path).exists()

data class FilePickerSetup(
    val rows: List<FilePickerRow>,
    val preselectedFileIds: Set<Long>
)

/**
 * Shared core of the cherry-pick flow, usable from Hilt delegates and the
 * manually-constructed DualScreenManager alike.
 */
@Singleton
class FilePickerFlowUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val downloadManager: com.nendo.argosy.data.download.DownloadManager,
    private val bgmPlaylistRepository: BgmPlaylistRepository,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val controlsPreferencesRepository: ControlsPreferencesRepository,
    private val attributionRepository: StorageAttributionRepository,
    private val gameRepository: com.nendo.argosy.data.repository.GameRepository,
    private val volumeHealth: StorageVolumeHealth,
    private val m3uManager: M3uManager
) {

    /**
     * Where an add-on actually sits, whatever layout put it there: the recorded path first, then
     * `extcontent/` and the category folders beside the base rom. A file found by probing has its
     * row repaired, so a layout the download did not record still reads as downloaded rather than
     * queueing a duplicate.
     */
    private suspend fun resolveOnDisk(file: GameFileEntity, gameFolder: File?): File? {
        file.localPath?.let { path ->
            val recorded = File(path)
            if (recorded.exists()) return recorded
        }
        if (gameFolder == null) return null
        val found = ZipExtractor.findAddonFile(gameFolder, file.fileName, file.category) ?: return null
        gameFileDao.updateLocalPath(file.id, found.absolutePath, Instant.now())
        return found
    }

    /**
     * The folder a game exclusively owns, or null when its rom sits directly in the platform folder.
     * A shared folder holds every game's files, so a bare filename found there proves nothing about
     * ownership and must never be adopted into this game's rows.
     */
    private suspend fun gameFolderOf(game: GameEntity): File? {
        val folder = game.localPath?.let { File(it).parentFile }?.takeIf { it.isDirectory } ?: return null
        val platformDir = gameRepository.getDownloadDirForPlatformId(game.platformId) ?: return null
        val folderCanonical = runCatching { folder.canonicalPath }.getOrNull() ?: return null
        val platformCanonical = runCatching { platformDir.canonicalPath }.getOrNull() ?: return null
        return folder.takeIf { folderCanonical != platformCanonical }
    }

    /** Null when there is nothing to choose: callers fall back to a plain download. */
    suspend fun buildRows(gameId: Long): FilePickerSetup? {
        val game = gameDao.getById(gameId) ?: return null
        val rommId = game.rommId ?: return null
        val dbRows = gameFileDao.getFilesForGame(gameId)
        val gameFolder = gameFolderOf(game)
        val defaults = preferencesRepository.getEffectiveDownloadDefaults(game.platformSlug)

        val includesUncategorised = DownloadDefaults.includesUncategorised(defaults, game.platformSlug)
        val preselectedFiles = mutableSetOf<Long>()

        val allFiles = when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> result.data.files
                ?.filter { it.isGameContent } ?: emptyList()
            is RomMResult.Error -> emptyList()
        }
        val (documents, romFiles) = allFiles.partition { it.category in DownloadDefaults.DOCUMENT_KEYS }
        if (romFiles.size <= 1) return null
        documents.forEach { preselectedFiles += it.id }

        val dbByRommId = dbRows.distinctBy { it.rommFileId }.associateBy { it.rommFileId }
        val entries = romFiles.map { f ->
            val downloaded = dbByRommId[f.id]?.let { resolveOnDisk(it, gameFolder) } != null
            f.toPickerEntry(isDownloaded = downloaded)
        }
        val rows = FilePickerTree.build(entries, lockSoleBaseFile = true)
        for (row in rows) {
            val fileId = row.rommFileId ?: continue
            val includeDefault = when {
                row.groupKey == VariantCategory.GAME.key -> true
                row.isUncategorised -> includesUncategorised
                else -> defaults[row.groupKey] ?: false
            }
            if (includeDefault) preselectedFiles += fileId
        }
        return FilePickerSetup(rows, preselectedFiles)
    }

    /**
     * Manage-mode rows come from the database only: every known file with its
     * on-disk state preselected, plus a locked row for the base game file.
     */
    suspend fun buildManageRows(gameId: Long): FilePickerSetup? {
        val game = gameDao.getById(gameId) ?: return null
        val dbRows = gameFileDao.getFilesForGame(gameId)
        if (dbRows.isEmpty()) return null

        val gameFolder = gameFolderOf(game)
        val rows = mutableListOf<FilePickerRow>()
        val preselected = mutableSetOf<Long>()

        val basePath = game.localPath
        val baseInDb = basePath != null && dbRows.any { it.localPath == basePath }
        if (basePath != null && !baseInDb && !game.isMultiDisc && File(basePath).exists()) {
            rows += FilePickerRow(
                isFolder = false,
                groupKey = VariantCategory.GAME.key,
                label = basePath.substringAfterLast('/'),
                sizeBytes = game.fileSizeBytes ?: 0,
                isDownloaded = true,
                isLocked = true
            )
        }

        val entries = dbRows.mapNotNull { f ->
            val rommFileId = f.rommFileId ?: return@mapNotNull null
            val resolved = resolveOnDisk(f, gameFolder)
            if (resolved != null) preselected += rommFileId
            f.toPickerEntry(
                rommFileId = rommFileId,
                isDownloaded = resolved != null,
                isLocked = resolved?.absolutePath == game.localPath
            )
        }
        rows += FilePickerTree.build(entries, lockSoleBaseFile = false)
        if (rows.none { !it.isFolder && !it.isLocked }) return null
        return FilePickerSetup(rows, preselected)
    }

    suspend fun applyManagedSelection(
        gameId: Long,
        rows: List<FilePickerRow>,
        selected: Set<Long>
    ): Pair<Int, Int> {
        val dbRows = gameFileDao.getFilesForGame(gameId)
        val byRommId = dbRows.associateBy { it.rommFileId }
        val gameFolder = gameDao.getById(gameId)?.let { gameFolderOf(it) }
        var added = 0
        var removed = 0
        for (row in rows) {
            val rommFileId = row.rommFileId ?: continue
            if (row.isFolder || row.isLocked) continue
            val db = byRommId[rommFileId] ?: continue
            val wantIt = rommFileId in selected
            val onDisk = resolveOnDisk(db, gameFolder)
            when {
                wantIt && onDisk == null -> {
                    val game = gameDao.getById(gameId) ?: continue
                    downloadManager.enqueueGameFileDownload(
                        gameId = gameId,
                        gameFileId = db.id,
                        rommFileId = rommFileId,
                        fileName = db.fileName,
                        category = db.category,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        coverPath = gameRepository.getArt(gameId).coverPath,
                        expectedSizeBytes = db.fileSize,
                        gameFolderName = game.rommFileName
                    )
                    added++
                }
                !wantIt && onDisk != null -> {
                    if (onDisk.delete()) {
                        gameFileDao.clearLocalPath(db.id)
                        m3uManager.recordDiscFile(gameId, db.fileName, null)
                        pruneMusicReferences(onDisk.absolutePath)
                        removed++
                    }
                }
            }
        }
        attributionRepository.markDirty(StorageCategory.GAMES)
        attributionRepository.markDirty(StorageCategory.MUSIC)
        return added to removed
    }

    /**
     * Deletes a game's soundtrack files (they live under the Music dir, outside the game dir, so
     * DeleteGameUseCase deliberately skips them). Reuses [pruneMusicReferences] and marks MUSIC dirty.
     */
    suspend fun purgeSoundtrack(gameId: Long): Int {
        val soundtracks = gameFileDao.getFilesByCategory(gameId, VariantCategory.SOUNDTRACK.key)
        val probe = volumeHealth.newProbe()
        var removed = 0
        for (file in soundtracks) {
            val path = file.localPath ?: continue
            if (!File(path).delete() && !probe.isGenuinelyAbsent(path)) continue
            gameFileDao.clearLocalPath(file.id)
            pruneMusicReferences(path)
            removed++
        }
        if (removed > 0) attributionRepository.markDirty(StorageCategory.MUSIC)
        return removed
    }

    private suspend fun pruneMusicReferences(path: String) {
        val musicDirPrefix = musicDirectoryManager.resolveMusicDir().absolutePath + File.separator
        if (!path.startsWith(musicDirPrefix)) return
        bgmPlaylistRepository.remove(path)
        val configs = controlsPreferencesRepository.preferences.first().soundConfigs
        val pruned = configs.filterValues { it.customFilePath != path }
        if (pruned.size != configs.size) {
            controlsPreferencesRepository.setSoundConfigs(pruned)
        }
    }

    /**
     * Returns queued count; the caller renders each returned issue via its own UI mapping.
     */
    suspend fun downloadSelection(
        gameId: Long,
        selectedFileIds: Set<Long>
    ): Pair<Int, List<DownloadResult>> {
        var queued = 0
        val issues = mutableListOf<DownloadResult>()
        val explicit = selectedFileIds.toList().takeIf { it.isNotEmpty() }
        when (val r = downloadGameUseCase(gameId, selectedFileIds = explicit)) {
            is DownloadResult.Queued -> queued++
            is DownloadResult.MultiDiscQueued -> queued += r.discCount
            is DownloadResult.AlreadyDownloaded -> issues += r
            is DownloadResult.Error -> issues += r
            else -> {}
        }
        return queued to issues
    }
}
