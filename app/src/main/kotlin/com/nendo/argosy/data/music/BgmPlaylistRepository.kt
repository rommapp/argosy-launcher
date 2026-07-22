package com.nendo.argosy.data.music

import com.nendo.argosy.core.media.AudioFileTypes
import com.nendo.argosy.data.local.dao.BgmPlaylistDao
import com.nendo.argosy.data.local.entity.BgmPlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BgmPlaylistRepository @Inject constructor(
    private val bgmPlaylistDao: BgmPlaylistDao
) {
    private val mutex = Mutex()

    fun observeAll(): Flow<List<BgmPlaylistEntity>> = bgmPlaylistDao.observeAll()

    /** Appends a track at the end of the playlist; re-enables a disabled existing row; no-op if already present and enabled. */
    suspend fun addFile(filePath: String, displayName: String, gameFileId: Long? = null): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val existing = bgmPlaylistDao.getByPath(filePath)
                if (existing != null) {
                    if (existing.enabled) return@withLock false
                    bgmPlaylistDao.updateEnabled(existing.id, true)
                    return@withLock true
                }
                val position = (bgmPlaylistDao.getMaxPosition() ?: -1) + 1
                bgmPlaylistDao.insert(
                    BgmPlaylistEntity(
                        position = position,
                        filePath = filePath,
                        displayName = displayName,
                        gameFileId = gameFileId,
                        entryType = BgmPlaylistEntity.TYPE_FILE
                    )
                )
                true
            }
        }

    /** Appends a folder sync source whose files are mirrored into file rows by reconcile; no-op if already present. */
    suspend fun addFolder(folderPath: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (bgmPlaylistDao.exists(folderPath)) return@withLock false
            val position = (bgmPlaylistDao.getMaxPosition() ?: -1) + 1
            bgmPlaylistDao.insert(
                BgmPlaylistEntity(
                    position = position,
                    filePath = folderPath,
                    displayName = folderPath.trimEnd('/').substringAfterLast('/'),
                    gameFileId = null,
                    entryType = BgmPlaylistEntity.TYPE_FOLDER
                )
            )
            true
        }
    }

    /**
     * Mirrors each folder source's recursive audio contents into file rows: inserts newly
     * discovered files at the end of the playlist and deletes sourced rows whose file is gone.
     * Manual rows and existing row positions are never touched. Returns whether anything changed.
     */
    suspend fun reconcileFolderSources(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            var changed = false
            val all = bgmPlaylistDao.getAll()
            val knownPaths = all.mapTo(mutableSetOf()) { it.filePath }
            var nextPosition = (bgmPlaylistDao.getMaxPosition() ?: -1) + 1
            all.filter { it.entryType == BgmPlaylistEntity.TYPE_FOLDER }.forEach { folder ->
                val dir = File(folder.filePath)
                if (!dir.isDirectory) return@forEach
                val discovered = dir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in AudioFileTypes.EXTENSIONS }
                    .map { it.absolutePath }
                    .sorted()
                    .toList()
                val discoveredSet = discovered.toSet()
                all.filter { it.sourceEntryId == folder.id && it.filePath !in discoveredSet }
                    .forEach { stale ->
                        bgmPlaylistDao.deleteById(stale.id)
                        knownPaths.remove(stale.filePath)
                        changed = true
                    }
                discovered.filter { it !in knownPaths }.forEach { path ->
                    val insertedId = bgmPlaylistDao.insertIgnore(
                        BgmPlaylistEntity(
                            position = nextPosition,
                            filePath = path,
                            displayName = File(path).nameWithoutExtension,
                            entryType = BgmPlaylistEntity.TYPE_FILE,
                            sourceEntryId = folder.id
                        )
                    )
                    if (insertedId != -1L) {
                        nextPosition++
                        knownPaths.add(path)
                        changed = true
                    }
                }
            }
            changed
        }
    }

    /** Ordered playable paths: enabled file rows in position order, filtered to files that exist. */
    suspend fun resolvePlaybackPaths(): List<String> = withContext(Dispatchers.IO) {
        bgmPlaylistDao.getAll()
            .filter { it.entryType != BgmPlaylistEntity.TYPE_FOLDER && it.enabled }
            .map { it.filePath }
            .filter { File(it).isFile }
    }

    suspend fun remove(filePath: String) = withContext(Dispatchers.IO) {
        mutex.withLock { bgmPlaylistDao.deleteByPath(filePath) }
    }

    suspend fun removeById(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock { bgmPlaylistDao.deleteById(id) }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock { bgmPlaylistDao.updateEnabled(id, enabled) }
    }

    /** True when the path lies inside any synced folder source. */
    suspend fun isCoveredBySyncedFolder(filePath: String): Boolean = withContext(Dispatchers.IO) {
        coveringFolderId(bgmPlaylistDao.getAll(), filePath) != null
    }

    /** Deletes a standalone manual row; disables a folder-sourced row; converts a folder-covered manual row to a disabled sourced row so it cannot silently return. */
    suspend fun removeOrDisable(filePath: String) = withContext(Dispatchers.IO) {
        mutex.withLock { bgmPlaylistDao.getByPath(filePath)?.let { removeOrDisableRow(it) } }
    }

    suspend fun removeOrDisableById(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock { bgmPlaylistDao.getById(id)?.let { removeOrDisableRow(it) } }
    }

    private suspend fun removeOrDisableRow(row: BgmPlaylistEntity) {
        if (row.entryType == BgmPlaylistEntity.TYPE_FOLDER) return
        if (row.sourceEntryId != null) {
            bgmPlaylistDao.updateEnabled(row.id, !row.enabled)
            return
        }
        val coveringId = coveringFolderId(bgmPlaylistDao.getAll(), row.filePath)
        if (coveringId == null) {
            bgmPlaylistDao.deleteById(row.id)
        } else {
            bgmPlaylistDao.convertToDisabledSourced(row.id, coveringId)
        }
    }

    private fun coveringFolderId(all: List<BgmPlaylistEntity>, filePath: String): Long? =
        all.firstOrNull { entry ->
            entry.entryType == BgmPlaylistEntity.TYPE_FOLDER &&
                filePath.startsWith(entry.filePath.trimEnd('/') + "/")
        }?.id

    /** Deletes a folder source row and every file row it mirrored, in one transaction. */
    suspend fun removeFolderSource(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock { bgmPlaylistDao.deleteFolderSource(id) }
    }

    suspend fun reorder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        mutex.withLock { bgmPlaylistDao.updatePositions(orderedIds) }
    }

    /** Repoints every folder-source and file row under [oldPrefix] to [newPrefix] after a relocation. */
    suspend fun rewritePathPrefix(oldPrefix: String, newPrefix: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            bgmPlaylistDao.rewritePathPrefix(oldPrefix.trimEnd('/'), newPrefix.trimEnd('/'))
        }
    }
}
