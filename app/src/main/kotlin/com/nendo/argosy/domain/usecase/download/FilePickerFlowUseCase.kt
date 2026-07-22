package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
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
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private fun pathOnDisk(path: String?): Boolean = path != null && File(path).exists()

data class FilePickerSetup(
    val rows: List<FilePickerRow>,
    val preselectedFileIds: Set<Long>,
    val preselectedVersionIds: Set<Long>
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
    private val attributionRepository: StorageAttributionRepository
) {

    /** Null when there is nothing to choose: callers fall back to a plain download. */
    suspend fun buildRows(gameId: Long): FilePickerSetup? {
        val game = gameDao.getById(gameId) ?: return null
        val rommId = game.rommId ?: return null
        val dbRows = gameFileDao.getFilesForGame(gameId)
        val defaults = preferencesRepository.getEffectiveDownloadDefaults(game.platformSlug)

        val rows = mutableListOf<FilePickerRow>()
        val preselectedFiles = mutableSetOf<Long>()
        val preselectedVersions = mutableSetOf<Long>()

        val versionGroups = dbRows.filter { it.versionGroup != null }.groupBy { it.versionGroup!! }
        if (versionGroups.size > 1) {
            rows += FilePickerRow(isHeader = true, groupKey = "versions", label = "Version")
            val defaultLaunch = game.activeVariantFileId
            versionGroups.forEach { (key, files) ->
                val memberRommId = key.removePrefix("romm:").toLongOrNull() ?: return@forEach
                val label = files.firstOrNull { it.regions != null }?.regions
                    ?: files.first().fileName
                val isDefault = defaultLaunch != null && files.any { it.id == defaultLaunch } ||
                    (defaultLaunch == null && memberRommId == rommId)
                rows += FilePickerRow(
                    isHeader = false,
                    groupKey = "versions",
                    label = label,
                    versionRommId = memberRommId,
                    sizeBytes = files.sumOf { it.fileSize },
                    isDownloaded = files.any { pathOnDisk(it.localPath) },
                    isDefaultVersion = isDefault
                )
                if (isDefault) preselectedVersions += memberRommId
            }
            if (preselectedVersions.isEmpty()) preselectedVersions += rommId
        }

        val romFiles = when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> result.data.files
                ?.filter { !it.fileName.startsWith(".") } ?: emptyList()
            is RomMResult.Error -> emptyList()
        }
        if (romFiles.size > 1) {
            val rootLen = romFiles.minOf { it.filePath.length }
            val grouped = romFiles.groupBy { f ->
                val cat = f.category
                when {
                    cat == VariantCategory.GAME.key || (cat == null && f.filePath.length == rootLen) -> "game"
                    cat != null -> cat
                    else -> "folder:${f.filePath.substringAfterLast('/')}"
                }
            }
            grouped.entries
                .sortedBy { (key, _) ->
                    if (key == "game") -1 else VariantCategory.fromKey(key).sortOrder
                }
                .forEach { (key, files) ->
                    val isGame = key == "game"
                    if (!isGame) {
                        val label = when {
                            key.startsWith("folder:") -> key.removePrefix("folder:")
                            else -> VariantCategory.fromKey(key).displayLabel
                        }
                        rows += FilePickerRow(isHeader = true, groupKey = key, label = label)
                    }
                    val includeDefault = when {
                        isGame -> true
                        key.startsWith("folder:") -> defaults[DownloadDefaults.OTHER_KEY] ?: false
                        else -> defaults[key] ?: false
                    }
                    files.sortedBy { it.fileName }.forEach { f ->
                        val downloaded = pathOnDisk(dbRows.firstOrNull { it.rommFileId == f.id }?.localPath)
                        rows += FilePickerRow(
                            isHeader = false,
                            groupKey = key,
                            label = f.fileName,
                            rommFileId = f.id,
                            sizeBytes = f.fileSizeBytes,
                            isDownloaded = downloaded,
                            isLocked = isGame && files.size == 1
                        )
                        if (includeDefault) preselectedFiles += f.id
                    }
                }
        }

        if (rows.none { !it.isHeader }) return null
        if (versionGroups.size <= 1 && romFiles.size <= 1) return null
        return FilePickerSetup(rows, preselectedFiles, preselectedVersions)
    }

    /**
     * Manage-mode rows come from the database only: every known file with its
     * on-disk state preselected, plus a locked row for the base game file.
     */
    suspend fun buildManageRows(gameId: Long): FilePickerSetup? {
        val game = gameDao.getById(gameId) ?: return null
        val dbRows = gameFileDao.getFilesForGame(gameId)
        if (dbRows.isEmpty()) return null

        val rows = mutableListOf<FilePickerRow>()
        val preselected = mutableSetOf<Long>()
        val rootDepth = dbRows.minOf { it.filePath.count { c -> c == '/' } }

        val basePath = game.localPath
        val baseInDb = basePath != null && dbRows.any { it.localPath == basePath }
        if (basePath != null && !baseInDb && File(basePath).exists()) {
            rows += FilePickerRow(
                isHeader = false,
                groupKey = "game",
                label = basePath.substringAfterLast('/'),
                sizeBytes = game.fileSizeBytes ?: 0,
                isDownloaded = true,
                isLocked = true
            )
        }

        val grouped = dbRows.groupBy { f ->
            val cat = VariantCategory.fromKey(f.category)
            when {
                cat == VariantCategory.GAME -> "game"
                cat != VariantCategory.UNKNOWN -> cat.key
                f.filePath.count { c -> c == '/' } > rootDepth -> "folder:${f.filePath.substringAfterLast('/')}"
                else -> "game"
            }
        }
        grouped.entries
            .sortedBy { (key, _) -> if (key == "game") -1 else VariantCategory.fromKey(key).sortOrder }
            .forEach { (key, files) ->
                if (key != "game") {
                    val label = when {
                        key.startsWith("folder:") -> key.removePrefix("folder:")
                        else -> VariantCategory.fromKey(key).displayLabel
                    }
                    rows += FilePickerRow(isHeader = true, groupKey = key, label = label)
                }
                files.sortedBy { it.fileName }.forEach { f ->
                    val rommFileId = f.rommFileId ?: return@forEach
                    val onDisk = f.localPath != null && File(f.localPath).exists()
                    val isBase = onDisk && f.localPath == game.localPath
                    rows += FilePickerRow(
                        isHeader = false,
                        groupKey = key,
                        label = f.regions?.let { "${f.fileName} ($it)" } ?: f.fileName,
                        rommFileId = rommFileId,
                        sizeBytes = f.fileSize,
                        isDownloaded = onDisk,
                        isLocked = isBase
                    )
                    if (onDisk) preselected += rommFileId
                }
            }
        if (rows.none { !it.isHeader && !it.isLocked }) return null
        return FilePickerSetup(rows, preselected, emptySet())
    }

    /** Checked-but-missing files get queued; unchecked-but-present files get deleted. Returns added to removed. */
    suspend fun applyManagedSelection(
        gameId: Long,
        rows: List<FilePickerRow>,
        selected: Set<Long>
    ): Pair<Int, Int> {
        val dbRows = gameFileDao.getFilesForGame(gameId)
        val byRommId = dbRows.associateBy { it.rommFileId }
        var added = 0
        var removed = 0
        for (row in rows) {
            val rommFileId = row.rommFileId ?: continue
            if (row.isHeader || row.isLocked) continue
            val db = byRommId[rommFileId] ?: continue
            val wantIt = rommFileId in selected
            val haveIt = db.localPath != null
            when {
                wantIt && !haveIt -> {
                    val game = gameDao.getById(gameId) ?: continue
                    downloadManager.enqueueGameFileDownload(
                        gameId = gameId,
                        gameFileId = db.id,
                        rommFileId = rommFileId,
                        fileName = db.fileName,
                        category = db.category,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        coverPath = game.coverPath,
                        expectedSizeBytes = db.fileSize,
                        gameFolderName = game.rommFileName
                    )
                    added++
                }
                !wantIt && haveIt -> {
                    val path = db.localPath
                    if (path != null && File(path).delete()) {
                        gameFileDao.clearLocalPath(db.id)
                        pruneMusicReferences(path)
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
        var removed = 0
        for (file in soundtracks) {
            val path = file.localPath ?: continue
            if (File(path).delete() || !File(path).exists()) {
                gameFileDao.clearLocalPath(file.id)
                pruneMusicReferences(path)
                removed++
            }
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

    /** Returns queued count; errors surface through the returned messages. */
    suspend fun downloadSelection(
        gameId: Long,
        selectedFileIds: Set<Long>,
        selectedVersionIds: Set<Long>
    ): Pair<Int, List<String>> {
        val primaryRommId = gameDao.getById(gameId)?.rommId
        var queued = 0
        val errors = mutableListOf<String>()
        if (primaryRommId == null || primaryRommId in selectedVersionIds || selectedVersionIds.isEmpty()) {
            val explicit = selectedFileIds.toList().takeIf { it.isNotEmpty() }
            when (val r = downloadGameUseCase(gameId, selectedFileIds = explicit)) {
                is DownloadResult.Queued -> queued++
                is DownloadResult.AlreadyDownloaded -> errors += "Game already downloaded"
                is DownloadResult.Error -> errors += r.message
                else -> {}
            }
        }
        selectedVersionIds.filter { it != primaryRommId }.forEach { versionId ->
            when (val r = downloadGameUseCase(gameId, versionRommId = versionId)) {
                is DownloadResult.Queued -> queued++
                is DownloadResult.Error -> errors += r.message
                else -> {}
            }
        }
        return queued to errors
    }
}
