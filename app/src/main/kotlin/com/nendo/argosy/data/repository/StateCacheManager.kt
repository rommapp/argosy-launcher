package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.StatePathConfig
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
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

data class DiscoveredState(
    val file: File,
    val slotNumber: Int,
    val lastModified: Instant
)

@Singleton
class StateCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateCacheDao: StateCacheDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val coreVersionExtractor: CoreVersionExtractor
) {
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())

    companion object {
        private const val TAG = "StateCacheManager"
        private const val MIN_UNLOCKED_SLOTS = 3
    }

    private val cacheBaseDir: File
        get() = File(context.filesDir, "state_cache")

    suspend fun discoverStatesForGame(
        gameId: Long,
        emulatorId: String,
        romPath: String,
        platformId: String
    ): List<DiscoveredState> = withContext(Dispatchers.IO) {
        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return@withContext emptyList()
        }

        val romFile = File(romPath)
        val romBaseName = romFile.nameWithoutExtension

        val statePaths = StatePathRegistry.resolvePath(config, platformId)
        val discovered = mutableListOf<DiscoveredState>()

        for (statePath in statePaths) {
            val stateDir = File(statePath)
            val states = StatePathRegistry.discoverStates(stateDir, romBaseName, config.slotPattern)
            for ((file, slotNumber) in states) {
                discovered.add(
                    DiscoveredState(
                        file = file,
                        slotNumber = slotNumber,
                        lastModified = Instant.ofEpochMilli(file.lastModified())
                    )
                )
            }
            if (discovered.isNotEmpty()) break
        }

        Log.d(TAG, "Discovered ${discovered.size} states for game $gameId")
        discovered
    }

    suspend fun cacheState(
        gameId: Long,
        emulatorId: String,
        slotNumber: Int,
        statePath: String,
        coreId: String? = null,
        coreVersion: String? = null,
        channelName: String? = null,
        isLocked: Boolean = false
    ): Long? = withContext(Dispatchers.IO) {
        val stateFile = File(statePath)
        if (!stateFile.exists()) {
            Log.w(TAG, "State file does not exist: $statePath")
            return@withContext null
        }

        val now = Instant.now()
        val timestamp = TIMESTAMP_FORMAT.format(now)
        val gameDir = File(cacheBaseDir, "$gameId/slot$slotNumber/$timestamp")

        try {
            gameDir.mkdirs()

            val cachedFile = File(gameDir, stateFile.name)
            stateFile.copyTo(cachedFile, overwrite = true)
            val cachePath = "$gameId/slot$slotNumber/$timestamp/${stateFile.name}"
            val stateSize = cachedFile.length()

            val entity = StateCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                slotNumber = slotNumber,
                channelName = channelName,
                cachedAt = now,
                stateSize = stateSize,
                cachePath = cachePath,
                coreId = coreId,
                coreVersion = coreVersion,
                isLocked = isLocked,
                note = null
            )

            val id = stateCacheDao.upsert(entity)
            Log.d(TAG, "Cached state for game $gameId slot $slotNumber at $cachePath")

            pruneOldCaches(gameId)
            id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache state", e)
            gameDir.deleteRecursively()
            null
        }
    }

    suspend fun restoreState(cacheId: Long, targetPath: String): Boolean = withContext(Dispatchers.IO) {
        val entity = stateCacheDao.getById(cacheId)
        if (entity == null) {
            Log.e(TAG, "State cache entry not found: $cacheId")
            return@withContext false
        }

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) {
            Log.e(TAG, "State cache file not found: ${entity.cachePath}")
            return@withContext false
        }

        try {
            val targetFile = File(targetPath)
            targetFile.parentFile?.mkdirs()
            cacheFile.copyTo(targetFile, overwrite = true)

            Log.d(TAG, "Restored state from cache $cacheId to $targetPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore state from cache", e)
            false
        }
    }

    suspend fun validateCoreVersion(
        cacheId: Long,
        currentCoreId: String?,
        currentVersion: String?
    ): VersionValidationResult = withContext(Dispatchers.IO) {
        val entity = stateCacheDao.getById(cacheId)
        if (entity == null) {
            return@withContext VersionValidationResult.Unknown
        }

        if (entity.coreId != null && currentCoreId != null && entity.coreId != currentCoreId) {
            return@withContext VersionValidationResult.Mismatch(
                savedVersion = "${entity.coreId}:${entity.coreVersion ?: "unknown"}",
                currentVersion = "$currentCoreId:${currentVersion ?: "unknown"}"
            )
        }

        coreVersionExtractor.validateVersion(entity.coreVersion, currentVersion)
    }

    suspend fun bindToChannel(cacheId: Long, channelName: String) = withContext(Dispatchers.IO) {
        stateCacheDao.bindToChannel(cacheId, channelName)
        Log.d(TAG, "Bound state cache $cacheId to channel '$channelName'")
    }

    suspend fun unbindFromChannel(cacheId: Long) = withContext(Dispatchers.IO) {
        stateCacheDao.unbindFromChannel(cacheId)
        Log.d(TAG, "Unbound state cache $cacheId from channel")
    }

    suspend fun setNote(cacheId: Long, note: String?) = withContext(Dispatchers.IO) {
        stateCacheDao.setNote(cacheId, note?.takeIf { it.isNotBlank() })
    }

    suspend fun deleteState(cacheId: Long) = withContext(Dispatchers.IO) {
        val entity = stateCacheDao.getById(cacheId) ?: return@withContext

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        val parentDir = cacheFile.parentFile
        cacheFile.delete()
        if (parentDir?.listFiles()?.isEmpty() == true) {
            parentDir.delete()
        }

        stateCacheDao.deleteById(cacheId)
        Log.d(TAG, "Deleted state cache $cacheId")
    }

    fun getStatesForGame(gameId: Long): Flow<List<StateCacheEntity>> =
        stateCacheDao.observeByGame(gameId)

    suspend fun getStatesForGameOnce(gameId: Long): List<StateCacheEntity> =
        stateCacheDao.getByGame(gameId)

    suspend fun getStatesForChannel(gameId: Long, channelName: String): List<StateCacheEntity> =
        stateCacheDao.getByChannel(gameId, channelName)

    suspend fun getDefaultChannelStates(gameId: Long): List<StateCacheEntity> =
        stateCacheDao.getDefaultChannel(gameId)

    suspend fun getStateBySlot(
        gameId: Long,
        emulatorId: String,
        slotNumber: Int,
        channelName: String? = null
    ): StateCacheEntity? = stateCacheDao.getBySlot(gameId, emulatorId, slotNumber, channelName)

    suspend fun getStateById(cacheId: Long): StateCacheEntity? =
        stateCacheDao.getById(cacheId)

    suspend fun pruneOldCaches(gameId: Long) = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferences.first()
        val limit = prefs.saveCacheLimit

        val totalCount = stateCacheDao.countByGame(gameId)
        if (totalCount <= limit) return@withContext

        val caches = stateCacheDao.getByGame(gameId)
        val lockedCount = caches.count { it.isLocked }
        val effectiveLimit = maxOf(limit, lockedCount + MIN_UNLOCKED_SLOTS)

        val toDeleteCount = totalCount - effectiveLimit
        if (toDeleteCount <= 0) return@withContext

        stateCacheDao.deleteOldestUnlocked(gameId, toDeleteCount)
        Log.d(TAG, "Pruned $toDeleteCount old state caches for game $gameId")
    }

    suspend fun deleteAllStatesForGame(gameId: Long) = withContext(Dispatchers.IO) {
        val caches = stateCacheDao.getByGame(gameId)
        for (cache in caches) {
            val cacheFile = File(cacheBaseDir, cache.cachePath)
            val parentDir = cacheFile.parentFile
            cacheFile.delete()
            if (parentDir?.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        }
        stateCacheDao.deleteByGame(gameId)

        val gameDir = File(cacheBaseDir, gameId.toString())
        if (gameDir.exists() && gameDir.isDirectory) {
            gameDir.deleteRecursively()
        }

        Log.d(TAG, "Deleted all ${caches.size} cached states for game $gameId")
    }

    fun buildStateTargetPath(
        config: StatePathConfig,
        platformId: String,
        romBaseName: String,
        slotNumber: Int
    ): String? {
        val paths = StatePathRegistry.resolvePath(config, platformId)
        val baseDir = paths.firstOrNull { File(it).exists() } ?: paths.firstOrNull() ?: return null
        val fileName = config.slotPattern.buildFileName(romBaseName, slotNumber)
        return "$baseDir/$fileName"
    }
}
