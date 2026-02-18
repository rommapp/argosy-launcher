package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.SaveDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveCacheDao: SaveCacheDao,
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val saveArchiver: SaveArchiver,
    private val fal: FileAccessLayer
) {
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())

    companion object {
        private const val TAG = "SaveCacheManager"
        private const val MIN_UNLOCKED_SLOTS = 5
    }

    sealed class CacheResult {
        data class Created(val timestamp: Long, val cacheId: Long = 0) : CacheResult()
        data object Duplicate : CacheResult()
        data object Failed : CacheResult()

        val success: Boolean get() = this != Failed
    }

    private val cacheBaseDir: File
        get() = File(context.filesDir, "save_cache")

    suspend fun cacheCurrentSave(
        gameId: Long,
        emulatorId: String,
        savePath: String,
        channelName: String? = null,
        isLocked: Boolean = false,
        cheatsUsed: Boolean = false,
        isHardcore: Boolean = false,
        slotName: String? = null,
        skipDuplicateCheck: Boolean = false,
        needsRemoteSync: Boolean = false
    ): CacheResult = withContext(Dispatchers.IO) {
        if (!fal.exists(savePath)) {
            Log.w(TAG, "Save file does not exist: $savePath")
            return@withContext CacheResult.Failed
        }

        val saveFile = fal.getTransformedFile(savePath)
        var tempFile: File? = null

        try {
            val (contentHash, tempOrSource) = if (fal.isDirectory(savePath)) {
                val folderHash = saveArchiver.calculateFolderAsZipHash(saveFile)
                tempFile = File(context.cacheDir, "temp_save_${System.currentTimeMillis()}.zip")
                if (!saveArchiver.zipFolder(saveFile, tempFile)) {
                    Log.e(TAG, "Failed to zip save folder")
                    return@withContext CacheResult.Failed
                }
                folderHash to tempFile
            } else {
                saveArchiver.calculateFileHash(saveFile) to saveFile
            }

            // Check for duplicate save by hash (skip for new games to allow fresh start saves)
            if (!skipDuplicateCheck) {
                val existingWithHash = saveCacheDao.getByGameAndHash(gameId, contentHash)
                if (existingWithHash != null) {
                    Log.d(TAG, "Duplicate save detected for game $gameId (hash=$contentHash, hardcore=$isHardcore), skipping cache")
                    SaveDebugLogger.logCacheDuplicate(
                        gameId = gameId,
                        gameName = null,
                        channel = channelName,
                        contentHash = contentHash
                    )
                    tempFile?.delete()
                    return@withContext CacheResult.Duplicate
                }
            }

            val now = Instant.now()
            val timestamp = TIMESTAMP_FORMAT.format(now)
            val gameDir = File(cacheBaseDir, "$gameId/$timestamp")
            gameDir.mkdirs()

            val (cachePath, cachedFile) = if (fal.isDirectory(savePath)) {
                val finalZip = File(gameDir, "save.zip")
                tempOrSource.renameTo(finalZip).let { renamed ->
                    if (!renamed) {
                        tempOrSource.copyTo(finalZip, overwrite = true)
                        tempOrSource.delete()
                    }
                }
                tempFile = null
                "$gameId/$timestamp/save.zip" to finalZip
            } else {
                val destFile = File(gameDir, saveFile.name)
                saveFile.copyTo(destFile, overwrite = true)
                "$gameId/$timestamp/${saveFile.name}" to destFile
            }

            if (isHardcore) {
                saveArchiver.appendHardcoreTrailer(cachedFile)
            }

            val saveSize = cachedFile.length()

            // For named slots, replace existing instead of creating new entries
            if (slotName != null) {
                val existing = saveCacheDao.getByGameAndSlot(gameId, slotName)
                if (existing != null) {
                    val oldFile = File(cacheBaseDir, existing.cachePath)
                    oldFile.delete()
                    oldFile.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
                    saveCacheDao.deleteById(existing.id)
                    Log.d(TAG, "Replaced existing slot '$slotName' for game $gameId")
                }
            }

            val entity = SaveCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                cachedAt = now,
                saveSize = saveSize,
                cachePath = cachePath,
                note = channelName,
                isLocked = isLocked,
                contentHash = contentHash,
                cheatsUsed = cheatsUsed,
                isHardcore = isHardcore,
                slotName = slotName,
                channelName = channelName,
                needsRemoteSync = needsRemoteSync
            )
            val insertedId = saveCacheDao.insert(entity)

            if (channelName != null) {
                gameDao.updateActiveSaveChannel(gameId, channelName)
                saveCacheDao.clearDirtyFlagForChannel(gameId, channelName, excludeId = insertedId)
            } else {
                saveCacheDao.clearDirtyFlagForLatest(gameId)
            }
            val slotInfo = when {
                isHardcore -> " [HARDCORE]"
                channelName != null -> " (channel: $channelName)"
                else -> ""
            }
            Log.d(TAG, "Cached save for game $gameId at $cachePath (hash=$contentHash)$slotInfo")

            SaveDebugLogger.logCacheCreated(
                gameId = gameId,
                gameName = null,
                channel = channelName,
                sizeBytes = saveSize,
                contentHash = contentHash,
                isHardcore = isHardcore,
                needsRemoteSync = needsRemoteSync,
                emulatorId = emulatorId
            )

            pruneOldCaches(gameId)
            CacheResult.Created(now.toEpochMilli(), insertedId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache save", e)
            tempFile?.delete()
            CacheResult.Failed
        }
    }

    suspend fun cacheServerDownload(
        gameId: Long,
        emulatorId: String,
        downloadedFile: File,
        channelName: String?,
        serverTimestamp: Instant? = null,
        isLocked: Boolean = false,
        needsRemoteSync: Boolean = false,
        rommSaveId: Long? = null
    ): CacheResult = withContext(Dispatchers.IO) {
        if (!downloadedFile.exists() || downloadedFile.length() == 0L) {
            Log.w(TAG, "Downloaded file missing or empty: ${downloadedFile.absolutePath}")
            return@withContext CacheResult.Failed
        }

        try {
            val isZip = downloadedFile.inputStream().use { input ->
                val magic = ByteArray(2)
                input.read(magic) == 2 &&
                    magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte()
            }

            val contentHash = if (isZip) {
                saveArchiver.calculateZipHash(downloadedFile)
            } else {
                saveArchiver.calculateFileHash(downloadedFile)
            }

            val now = serverTimestamp ?: Instant.now()
            val timestamp = TIMESTAMP_FORMAT.format(now)
            val gameDir = File(cacheBaseDir, "$gameId/$timestamp")
            gameDir.mkdirs()

            val (cachePath, cachedFile) = if (isZip) {
                val finalZip = File(gameDir, "save.zip")
                downloadedFile.copyTo(finalZip, overwrite = true)
                "$gameId/$timestamp/save.zip" to finalZip
            } else {
                val destFile = File(gameDir, downloadedFile.name)
                downloadedFile.copyTo(destFile, overwrite = true)
                "$gameId/$timestamp/${downloadedFile.name}" to destFile
            }

            val saveSize = cachedFile.length()

            val entity = SaveCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                cachedAt = now,
                saveSize = saveSize,
                cachePath = cachePath,
                note = channelName,
                isLocked = isLocked,
                contentHash = contentHash,
                channelName = channelName,
                needsRemoteSync = needsRemoteSync,
                rommSaveId = rommSaveId
            )
            val insertedId = saveCacheDao.insert(entity)

            if (channelName != null) {
                gameDao.updateActiveSaveChannel(gameId, channelName)
                saveCacheDao.clearDirtyFlagForLatest(gameId)
            }

            Log.d(TAG, "Cached server download for game $gameId at $cachePath (zip=$isZip, channel=$channelName)")

            SaveDebugLogger.logCacheCreated(
                gameId = gameId,
                gameName = null,
                channel = channelName,
                sizeBytes = saveSize,
                contentHash = contentHash,
                isHardcore = false,
                needsRemoteSync = needsRemoteSync,
                emulatorId = emulatorId
            )

            pruneOldCaches(gameId)
            CacheResult.Created(now.toEpochMilli(), insertedId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache server download", e)
            CacheResult.Failed
        }
    }

    suspend fun cacheAsRollback(
        gameId: Long,
        emulatorId: String,
        savePath: String
    ): CacheResult = withContext(Dispatchers.IO) {
        if (!fal.exists(savePath)) {
            Log.w(TAG, "Save file does not exist for rollback: $savePath")
            return@withContext CacheResult.Failed
        }

        val saveFile = fal.getTransformedFile(savePath)
        var tempFile: File? = null

        try {
            val (contentHash, tempOrSource) = if (fal.isDirectory(savePath)) {
                val folderHash = saveArchiver.calculateFolderAsZipHash(saveFile)
                tempFile = File(context.cacheDir, "temp_rollback_${System.currentTimeMillis()}.zip")
                if (!saveArchiver.zipFolder(saveFile, tempFile)) {
                    Log.e(TAG, "Failed to zip save folder for rollback")
                    return@withContext CacheResult.Failed
                }
                folderHash to tempFile
            } else {
                saveArchiver.calculateFileHash(saveFile) to saveFile
            }

            val existingWithHash = saveCacheDao.getByGameAndHash(gameId, contentHash)
            if (existingWithHash != null) {
                Log.d(TAG, "Rollback skipped - identical save already cached (hash=$contentHash)")
                tempFile?.delete()
                return@withContext CacheResult.Duplicate
            }

            val now = Instant.now()
            val timestamp = TIMESTAMP_FORMAT.format(now)
            val gameDir = File(cacheBaseDir, "$gameId/$timestamp")
            gameDir.mkdirs()

            val (cachePath, cachedFile) = if (fal.isDirectory(savePath)) {
                val finalZip = File(gameDir, "save.zip")
                tempOrSource.renameTo(finalZip).let { renamed ->
                    if (!renamed) {
                        tempOrSource.copyTo(finalZip, overwrite = true)
                        tempOrSource.delete()
                    }
                }
                tempFile = null
                "$gameId/$timestamp/save.zip" to finalZip
            } else {
                val destFile = File(gameDir, saveFile.name)
                saveFile.copyTo(destFile, overwrite = true)
                "$gameId/$timestamp/${saveFile.name}" to destFile
            }

            val saveSize = cachedFile.length()

            val entity = SaveCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                cachedAt = now,
                saveSize = saveSize,
                cachePath = cachePath,
                note = "Rollback",
                isLocked = false,
                contentHash = contentHash,
                isRollback = true
            )
            saveCacheDao.insert(entity)
            Log.d(TAG, "Created rollback save for game $gameId at $cachePath")

            CacheResult.Created(now.toEpochMilli())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache rollback save", e)
            tempFile?.delete()
            CacheResult.Failed
        }
    }

    suspend fun restoreSave(cacheId: Long, targetPath: String): Boolean = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId)
        if (entity == null) {
            Log.e(TAG, "Cache entry not found: $cacheId")
            return@withContext false
        }

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) {
            Log.e(TAG, "Cache file not found: ${entity.cachePath}")
            return@withContext false
        }

        try {
            if (entity.cachePath.endsWith(".zip")) {
                fal.mkdirs(targetPath)
                val targetFile = fal.getTransformedFile(targetPath)
                saveArchiver.unzipSingleFolder(cacheFile, targetFile)
            } else {
                val parentPath = targetPath.substringBeforeLast('/')
                if (parentPath.isNotEmpty() && parentPath != targetPath) {
                    fal.mkdirs(parentPath)
                }
                val bytesWithoutTrailer = saveArchiver.readBytesWithoutTrailer(cacheFile)
                if (bytesWithoutTrailer != null) {
                    fal.writeBytes(targetPath, bytesWithoutTrailer)
                } else {
                    fal.copyFile(cacheFile.absolutePath, targetPath)
                }
            }

            Log.d(TAG, "Restored save from cache $cacheId to $targetPath")

            SaveDebugLogger.logCacheRestored(
                gameId = entity.gameId,
                gameName = null,
                channel = entity.channelName,
                cacheId = cacheId,
                targetPath = targetPath
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore save from cache", e)
            SaveDebugLogger.logError(
                operation = "restoreSave",
                gameId = entity.gameId,
                gameName = null,
                channel = entity.channelName,
                error = e
            )
            false
        }
    }

    suspend fun deleteCachedSave(cacheId: Long) = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId) ?: return@withContext

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        val parentDir = cacheFile.parentFile
        cacheFile.delete()
        if (parentDir?.listFiles()?.isEmpty() == true) {
            parentDir.delete()
        }

        saveCacheDao.deleteById(cacheId)
        Log.d(TAG, "Deleted cached save $cacheId")

        SaveDebugLogger.logCacheDeleted(
            gameId = entity.gameId,
            gameName = null,
            channel = entity.channelName,
            cacheId = cacheId,
            reason = "user_delete"
        )
    }

    suspend fun renameSave(cacheId: Long, name: String?) = withContext(Dispatchers.IO) {
        saveCacheDao.setNote(cacheId, name?.takeIf { it.isNotBlank() })
    }

    suspend fun copyToChannel(cacheId: Long, channelName: String): Long? = withContext(Dispatchers.IO) {
        val source = saveCacheDao.getById(cacheId)
        if (source == null) {
            Log.e(TAG, "Source cache entry not found: $cacheId")
            return@withContext null
        }

        val sourceFile = File(cacheBaseDir, source.cachePath)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source cache file not found: ${source.cachePath}")
            return@withContext null
        }

        val now = Instant.now()
        val timestamp = TIMESTAMP_FORMAT.format(now)
        val gameDir = File(cacheBaseDir, "${source.gameId}/$timestamp")

        try {
            gameDir.mkdirs()
            val destFile = File(gameDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)

            val cachePath = "${source.gameId}/$timestamp/${sourceFile.name}"
            val entity = SaveCacheEntity(
                gameId = source.gameId,
                emulatorId = source.emulatorId,
                cachedAt = now,
                saveSize = destFile.length(),
                cachePath = cachePath,
                note = channelName,
                isLocked = true,
                contentHash = source.contentHash,
                channelName = channelName
            )

            val newId = saveCacheDao.insert(entity)
            Log.d(TAG, "Created channel '$channelName' from cache $cacheId -> $newId")
            newId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy cache to channel", e)
            gameDir.deleteRecursively()
            null
        }
    }

    suspend fun deleteSave(cacheId: Long) = deleteCachedSave(cacheId)

    fun getCachesForGame(gameId: Long): Flow<List<SaveCacheEntity>> =
        saveCacheDao.observeByGame(gameId)

    suspend fun getCachesForGameOnce(gameId: Long): List<SaveCacheEntity> =
        saveCacheDao.getByGame(gameId)

    suspend fun pruneOldCaches(gameId: Long) = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferences.first()
        val limit = prefs.saveCacheLimit

        val totalCount = saveCacheDao.countByGame(gameId)
        if (totalCount <= limit) return@withContext

        val caches = saveCacheDao.getByGame(gameId)
        val lockedCount = caches.count { it.isLocked }
        val effectiveLimit = maxOf(limit, lockedCount + MIN_UNLOCKED_SLOTS)

        val toDeleteCount = totalCount - effectiveLimit
        if (toDeleteCount <= 0) return@withContext

        val unlocked = saveCacheDao.getOldestUnlocked(gameId)
        val toDelete = unlocked.take(toDeleteCount)

        for (cache in toDelete) {
            val cacheFile = File(cacheBaseDir, cache.cachePath)
            val parentDir = cacheFile.parentFile
            cacheFile.delete()
            if (parentDir?.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        }

        saveCacheDao.deleteOldestUnlocked(gameId, toDeleteCount)
        Log.d(TAG, "Pruned $toDeleteCount old caches for game $gameId")

        SaveDebugLogger.logCachePruned(
            gameId = gameId,
            gameName = null,
            prunedCount = toDeleteCount,
            remainingCount = totalCount - toDeleteCount
        )
    }

    suspend fun getCacheById(cacheId: Long): SaveCacheEntity? =
        saveCacheDao.getById(cacheId)

    suspend fun getLatestHardcoreSave(gameId: Long): SaveCacheEntity? =
        saveCacheDao.getLatestHardcoreSave(gameId)

    suspend fun hasHardcoreSave(gameId: Long): Boolean =
        saveCacheDao.hasHardcoreSave(gameId)

    suspend fun isValidHardcoreSave(entity: SaveCacheEntity): Boolean = withContext(Dispatchers.IO) {
        if (!entity.isHardcore) return@withContext false
        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) return@withContext false
        saveArchiver.hasHardcoreTrailer(cacheFile)
    }

    suspend fun getLatestCasualSave(gameId: Long, channelName: String?): SaveCacheEntity? {
        return if (channelName != null) {
            saveCacheDao.getLatestCasualSaveInChannel(gameId, channelName)
        } else {
            saveCacheDao.getLatestCasualSave(gameId)
        }
    }

    suspend fun getMostRecentSave(gameId: Long): SaveCacheEntity? =
        saveCacheDao.getMostRecent(gameId)

    suspend fun getByTimestamp(gameId: Long, timestampMillis: Long): SaveCacheEntity? =
        saveCacheDao.getByTimestamp(gameId, timestampMillis)

    suspend fun getMostRecentInChannel(gameId: Long, channelName: String): SaveCacheEntity? =
        saveCacheDao.getMostRecentInChannel(gameId, channelName)

    suspend fun getByGameAndHash(gameId: Long, hash: String): SaveCacheEntity? =
        saveCacheDao.getByGameAndHash(gameId, hash)

    suspend fun getSaveBytes(cacheId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId) ?: return@withContext null
        getSaveBytesFromEntity(entity)
    }

    suspend fun getSaveBytesFromEntity(entity: SaveCacheEntity): ByteArray? = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) {
            Log.e(TAG, "Cache file not found: ${entity.cachePath}")
            return@withContext null
        }

        try {
            if (cacheFile.name.endsWith(".zip")) {
                // Extract save from zip archive to a temp dir, find .srm file
                val tempDir = File(context.cacheDir, "save_extract_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                if (saveArchiver.unzipToFolder(cacheFile, tempDir)) {
                    val srmFile = tempDir.walkTopDown().firstOrNull { it.extension == "srm" }
                    val bytes = srmFile?.readBytes()
                    tempDir.deleteRecursively()
                    bytes
                } else {
                    tempDir.deleteRecursively()
                    null
                }
            } else {
                saveArchiver.readBytesWithoutTrailer(cacheFile) ?: cacheFile.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cache file: ${entity.cachePath}", e)
            null
        }
    }

    suspend fun deleteAllCachesForGame(gameId: Long) = withContext(Dispatchers.IO) {
        val caches = saveCacheDao.getByGame(gameId)
        for (cache in caches) {
            val cacheFile = File(cacheBaseDir, cache.cachePath)
            val parentDir = cacheFile.parentFile
            cacheFile.delete()
            if (parentDir?.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        }
        saveCacheDao.deleteByGame(gameId)

        val gameDir = File(cacheBaseDir, gameId.toString())
        if (gameDir.exists() && gameDir.isDirectory) {
            gameDir.deleteRecursively()
        }

        Log.d(TAG, "Deleted all ${caches.size} cached saves for game $gameId")
    }

    fun getCacheFile(entity: SaveCacheEntity): File = File(cacheBaseDir, entity.cachePath)

    suspend fun calculateLocalSaveHash(savePath: String): String? = withContext(Dispatchers.IO) {
        if (!fal.exists(savePath)) return@withContext null
        try {
            val saveFile = fal.getTransformedFile(savePath)
            if (fal.isDirectory(savePath)) {
                saveArchiver.calculateFolderAsZipHash(saveFile)
            } else {
                saveArchiver.calculateContentHash(saveFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate hash for $savePath", e)
            null
        }
    }
}
