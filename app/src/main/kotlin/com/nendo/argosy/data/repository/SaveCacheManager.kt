package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.SaveArchiver
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
    private val preferencesRepository: UserPreferencesRepository,
    private val saveArchiver: SaveArchiver
) {
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())

    companion object {
        private const val TAG = "SaveCacheManager"
        private const val MIN_UNLOCKED_SLOTS = 5
    }

    sealed class CacheResult {
        data object Created : CacheResult()
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
        slotName: String? = null
    ): CacheResult = withContext(Dispatchers.IO) {
        val saveFile = File(savePath)
        if (!saveFile.exists()) {
            Log.w(TAG, "Save file does not exist: $savePath")
            return@withContext CacheResult.Failed
        }

        var tempFile: File? = null

        try {
            val (contentHash, tempOrSource) = if (saveFile.isDirectory) {
                tempFile = File(context.cacheDir, "temp_save_${System.currentTimeMillis()}.zip")
                if (!saveArchiver.zipFolder(saveFile, tempFile)) {
                    Log.e(TAG, "Failed to zip save folder")
                    return@withContext CacheResult.Failed
                }
                saveArchiver.calculateFileHash(tempFile) to tempFile
            } else {
                saveArchiver.calculateFileHash(saveFile) to saveFile
            }

            val existingWithHash = saveCacheDao.getByGameAndHash(gameId, contentHash)
            if (existingWithHash != null) {
                Log.d(TAG, "Duplicate save detected for game $gameId (hash=$contentHash), skipping cache")
                tempFile?.delete()
                return@withContext CacheResult.Duplicate
            }

            val now = Instant.now()
            val timestamp = TIMESTAMP_FORMAT.format(now)
            val gameDir = File(cacheBaseDir, "$gameId/$timestamp")
            gameDir.mkdirs()

            val (cachePath, saveSize) = if (saveFile.isDirectory) {
                val finalZip = File(gameDir, "save.zip")
                tempOrSource.renameTo(finalZip).let { renamed ->
                    if (!renamed) {
                        tempOrSource.copyTo(finalZip, overwrite = true)
                        tempOrSource.delete()
                    }
                }
                tempFile = null
                "$gameId/$timestamp/save.zip" to finalZip.length()
            } else {
                val cachedFile = File(gameDir, saveFile.name)
                saveFile.copyTo(cachedFile, overwrite = true)
                "$gameId/$timestamp/${saveFile.name}" to cachedFile.length()
            }

            // For hardcore slots, replace existing instead of creating new entries
            val effectiveSlotName = if (isHardcore) SaveCacheEntity.SLOT_HARDCORE else slotName
            if (effectiveSlotName != null) {
                val existing = saveCacheDao.getByGameAndSlot(gameId, effectiveSlotName)
                if (existing != null) {
                    val oldFile = File(cacheBaseDir, existing.cachePath)
                    oldFile.delete()
                    oldFile.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
                    saveCacheDao.deleteById(existing.id)
                    Log.d(TAG, "Replaced existing slot '$effectiveSlotName' for game $gameId")
                }
            }

            val entity = SaveCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                cachedAt = now,
                saveSize = saveSize,
                cachePath = cachePath,
                note = channelName,
                isLocked = isLocked || isHardcore,
                contentHash = contentHash,
                cheatsUsed = cheatsUsed,
                isHardcore = isHardcore,
                slotName = effectiveSlotName
            )
            saveCacheDao.insert(entity)
            Log.d(TAG, "Cached save for game $gameId at $cachePath (hash=$contentHash)${channelName?.let { " (channel: $it)" } ?: ""}")

            pruneOldCaches(gameId)
            CacheResult.Created
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache save", e)
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
            val targetFile = File(targetPath)

            if (entity.cachePath.endsWith(".zip")) {
                if (targetFile.exists()) {
                    targetFile.deleteRecursively()
                }
                targetFile.mkdirs()
                saveArchiver.unzipSingleFolder(cacheFile, targetFile)
            } else {
                targetFile.parentFile?.mkdirs()
                cacheFile.copyTo(targetFile, overwrite = true)
            }

            Log.d(TAG, "Restored save from cache $cacheId to $targetPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore save from cache", e)
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
                contentHash = source.contentHash
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
    }

    suspend fun getCacheById(cacheId: Long): SaveCacheEntity? =
        saveCacheDao.getById(cacheId)

    suspend fun getHardcoreSlot(gameId: Long): SaveCacheEntity? =
        saveCacheDao.getHardcoreSlot(gameId)

    suspend fun deleteHardcoreSlot(gameId: Long) = withContext(Dispatchers.IO) {
        val hardcore = saveCacheDao.getHardcoreSlot(gameId) ?: return@withContext
        val cacheFile = File(cacheBaseDir, hardcore.cachePath)
        val parentDir = cacheFile.parentFile
        cacheFile.delete()
        if (parentDir?.listFiles()?.isEmpty() == true) {
            parentDir.delete()
        }
        saveCacheDao.deleteById(hardcore.id)
        Log.d(TAG, "Deleted hardcore slot for game $gameId")
    }

    suspend fun hasHardcoreSlot(gameId: Long): Boolean =
        saveCacheDao.getHardcoreSlot(gameId) != null

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
}
