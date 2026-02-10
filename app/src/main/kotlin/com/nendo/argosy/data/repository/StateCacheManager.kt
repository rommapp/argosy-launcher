package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.StatePathConfig
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.sync.SaveStatePayload
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
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
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val retroArchConfigParser: RetroArchConfigParser,
    private val saveSyncRepository: dagger.Lazy<SaveSyncRepository>
) {
    companion object {
        private const val TAG = "StateCacheManager"
        private const val MIN_UNLOCKED_SLOTS = 3
        private val STATE_EXTENSIONS = setOf("state", "state0", "state1", "state2", "state3", "state4", "state5", "state6", "state7", "state8", "state9", "auto")
        private const val BUFFER_SIZE = 8192
        private val SLOT_REGEX = Regex("""\.state(\d+)?$""")
        private val UNDERSCORE_SLOT_REGEX = Regex("""_(\d+)\.[^.]+$""")
    }

    private val cacheBaseDir: File
        get() = File(context.filesDir, "state_cache")

    @Volatile
    private var legacyCacheCleared = false

    private fun clearLegacyCacheIfNeeded() {
        if (legacyCacheCleared) return
        synchronized(this) {
            if (legacyCacheCleared) return
            if (!cacheBaseDir.exists()) {
                legacyCacheCleared = true
                return
            }
            val subdirs = cacheBaseDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            for (subdir in subdirs) {
                if (subdir.name.toLongOrNull() != null) {
                    Log.d(TAG, "Clearing legacy cache directory: ${subdir.name}")
                    subdir.deleteRecursively()
                }
            }
            legacyCacheCleared = true
        }
    }

    suspend fun discoverStatesForGame(
        gameId: Long,
        emulatorId: String,
        romPath: String,
        platformId: String,
        emulatorPackage: String? = null,
        coreName: String? = null
    ): List<DiscoveredState> = withContext(Dispatchers.IO) {
        Log.d(TAG, "discoverStatesForGame: gameId=$gameId, emulatorId=$emulatorId, platformId=$platformId")
        Log.d(TAG, "romPath=$romPath")

        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return@withContext emptyList()
        }

        val romFile = File(romPath)
        val romBaseName = romFile.nameWithoutExtension
        Log.d(TAG, "romBaseName=$romBaseName")

        val statePaths = if (emulatorId.startsWith("retroarch") && emulatorPackage != null) {
            val contentDir = romFile.parentFile?.absolutePath
            retroArchConfigParser.resolveStatePaths(emulatorPackage, coreName, contentDir)
        } else {
            StatePathRegistry.resolvePath(config, platformId)
        }
        Log.d(TAG, "Searching ${statePaths.size} paths: $statePaths")

        val discovered = mutableListOf<DiscoveredState>()

        for (statePath in statePaths) {
            val stateDir = File(statePath)
            Log.d(TAG, "Checking dir: $statePath exists=${stateDir.exists()} isDir=${stateDir.isDirectory}")
            if (stateDir.exists() && stateDir.isDirectory) {
                val files = stateDir.listFiles()?.map { it.name } ?: emptyList()
                Log.d(TAG, "Files in $statePath: $files")
            }
            val states = StatePathRegistry.discoverStates(stateDir, romBaseName, config.slotPattern)
            Log.d(TAG, "Found ${states.size} matching states in $statePath")
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
        platformSlug: String,
        emulatorId: String,
        slotNumber: Int,
        statePath: String,
        coreId: String? = null,
        coreVersion: String? = null,
        channelName: String? = null,
        isLocked: Boolean = false
    ): Long? = withContext(Dispatchers.IO) {
        clearLegacyCacheIfNeeded()

        val stateFile = File(statePath)
        if (!stateFile.exists()) {
            Log.w(TAG, "State file does not exist: $statePath")
            return@withContext null
        }

        val now = Instant.now()
        val coreDir = getCoreDir(gameId, platformSlug, channelName, coreId)

        try {
            coreDir.mkdirs()

            val cachedFile = File(coreDir, stateFile.name)
            stateFile.copyTo(cachedFile, overwrite = true)
            val channelDirName = channelName ?: "default"
            val coreDirName = coreId ?: "unknown"
            val cachePath = "$platformSlug/$gameId/$channelDirName/$coreDirName/${stateFile.name}"
            val stateSize = cachedFile.length()

            var screenshotCachePath: String? = null
            val screenshotFile = File("$statePath.png")
            Log.d(TAG, "Looking for screenshot at: ${screenshotFile.absolutePath} exists=${screenshotFile.exists()}")
            if (screenshotFile.exists()) {
                val cachedScreenshot = File(coreDir, screenshotFile.name)
                screenshotFile.copyTo(cachedScreenshot, overwrite = true)
                screenshotCachePath = "$platformSlug/$gameId/$channelDirName/$coreDirName/${screenshotFile.name}"
                Log.d(TAG, "Cached screenshot at $screenshotCachePath")
            } else {
                Log.d(TAG, "No screenshot found for state: $statePath")
            }

            val entity = StateCacheEntity(
                gameId = gameId,
                platformSlug = platformSlug,
                emulatorId = emulatorId,
                slotNumber = slotNumber,
                channelName = channelName,
                cachedAt = now,
                stateSize = stateSize,
                cachePath = cachePath,
                screenshotPath = screenshotCachePath,
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
            coreDir.deleteRecursively()
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

    fun getScreenshotPath(entity: StateCacheEntity): String? {
        val relativePath = entity.screenshotPath ?: return null
        return File(cacheBaseDir, relativePath).absolutePath
    }

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

    fun getChannelDir(gameId: Long, platformSlug: String, channelName: String?): File {
        val channelDirName = channelName ?: "default"
        return File(cacheBaseDir, "$platformSlug/$gameId/$channelDirName")
    }

    fun getCoreDir(gameId: Long, platformSlug: String, channelName: String?, coreId: String?): File {
        val channelDirName = channelName ?: "default"
        val coreDirName = coreId ?: "unknown"
        return File(cacheBaseDir, "$platformSlug/$gameId/$channelDirName/$coreDirName")
    }

    fun getCacheFile(entity: StateCacheEntity): File? {
        val file = File(cacheBaseDir, entity.cachePath)
        return if (file.exists()) file else null
    }

    fun getScreenshotFile(entity: StateCacheEntity): File? {
        val relativePath = entity.screenshotPath ?: return null
        val file = File(cacheBaseDir, relativePath)
        return if (file.exists()) file else null
    }

    suspend fun deleteStatesForChannel(gameId: Long, channelName: String?) = withContext(Dispatchers.IO) {
        val states = if (channelName != null) {
            stateCacheDao.getByChannel(gameId, channelName)
        } else {
            stateCacheDao.getDefaultChannel(gameId)
        }

        for (state in states) {
            val cacheFile = File(cacheBaseDir, state.cachePath)
            cacheFile.delete()
            state.screenshotPath?.let { File(cacheBaseDir, it).delete() }
        }

        stateCacheDao.deleteByChannel(gameId, channelName)
        Log.d(TAG, "Deleted ${states.size} states for game $gameId channel ${channelName ?: "default"}")
    }

    suspend fun deleteStatesForChannelAndCore(gameId: Long, channelName: String?, coreId: String?) = withContext(Dispatchers.IO) {
        val states = stateCacheDao.getByChannelAndCore(gameId, channelName, coreId)

        for (state in states) {
            val cacheFile = File(cacheBaseDir, state.cachePath)
            cacheFile.delete()
            state.screenshotPath?.let { File(cacheBaseDir, it).delete() }
        }

        stateCacheDao.deleteByChannelAndCore(gameId, channelName, coreId)
        Log.d(TAG, "Deleted ${states.size} states for game $gameId channel ${channelName ?: "default"} core ${coreId ?: "unknown"}")
    }

    suspend fun getStatesForChannelAndCore(gameId: Long, channelName: String?, coreId: String?): List<StateCacheEntity> =
        stateCacheDao.getByChannelAndCore(gameId, channelName, coreId)

    suspend fun deleteAutoStateFromDisk(
        emulatorId: String,
        romPath: String,
        platformSlug: String,
        emulatorPackage: String?,
        coreId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "deleteAutoStateFromDisk: No state config for emulator: $emulatorId")
            return@withContext false
        }

        val romFile = File(romPath)
        val romBaseName = romFile.nameWithoutExtension

        val statePaths = if (emulatorId.startsWith("retroarch") && emulatorPackage != null) {
            val contentDir = romFile.parentFile?.absolutePath
            retroArchConfigParser.resolveStatePaths(emulatorPackage, coreId, contentDir)
        } else {
            StatePathRegistry.resolvePath(config, platformSlug)
        }

        val autoFileName = config.slotPattern.buildFileName(romBaseName, -1)

        for (path in statePaths) {
            val stateDir = File(path)
            if (!stateDir.exists()) continue

            val autoStateFile = File(stateDir, autoFileName)
            if (autoStateFile.exists()) {
                autoStateFile.delete()
                File("${autoStateFile.absolutePath}.png").takeIf { it.exists() }?.delete()
                Log.d(TAG, "Deleted auto-state from disk: ${autoStateFile.absolutePath}")
                return@withContext true
            }
        }

        Log.d(TAG, "deleteAutoStateFromDisk: No auto-state found for $romBaseName")
        false
    }

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        if (cacheBaseDir.exists()) {
            cacheBaseDir.deleteRecursively()
            cacheBaseDir.mkdirs()
        }
        Log.d(TAG, "Cleared all state cache")
    }

    sealed class StateCloudResult {
        data object Success : StateCloudResult()
        data object NotConfigured : StateCloudResult()
        data object NoStateFound : StateCloudResult()
        data object AlreadySynced : StateCloudResult()
        data class Conflict(val localTime: Instant, val serverTime: Instant) : StateCloudResult()
        data class Error(val message: String) : StateCloudResult()
    }

    suspend fun uploadStateToRomM(
        state: StateCacheEntity,
        rommId: Long,
        api: RomMApi
    ): StateCloudResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "[StateSync] UPLOAD stateId=${state.id} gameId=${state.gameId} slot=${state.slotNumber}")

        val cacheFile = getCacheFile(state)
        if (cacheFile == null || !cacheFile.exists()) {
            Log.w(TAG, "[StateSync] UPLOAD stateId=${state.id} | Cache file not found")
            return@withContext StateCloudResult.NoStateFound
        }

        try {
            val contentHash = calculateFileHash(cacheFile)
            if (state.lastUploadedHash == contentHash) {
                Log.d(TAG, "[StateSync] UPLOAD stateId=${state.id} | Skipped - content unchanged (hash=$contentHash)")
                return@withContext StateCloudResult.AlreadySynced
            }

            val uploadFileName = File(state.cachePath).name
            val requestBody = cacheFile.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, requestBody)

            val response = if (state.rommSaveId != null) {
                api.updateSave(state.rommSaveId, filePart)
            } else {
                api.uploadSave(rommId, state.emulatorId, filePart)
            }

            if (response.isSuccessful) {
                val serverSave = response.body()
                if (serverSave == null) {
                    Log.e(TAG, "[StateSync] UPLOAD stateId=${state.id} | Empty response")
                    return@withContext StateCloudResult.Error("Empty response from server")
                }

                val serverTimestamp = parseTimestamp(serverSave.updatedAt)
                stateCacheDao.updateSyncState(
                    id = state.id,
                    rommSaveId = serverSave.id,
                    syncStatus = StateCacheEntity.STATUS_SYNCED,
                    serverUpdatedAt = serverTimestamp?.toEpochMilli(),
                    lastUploadedHash = contentHash
                )

                Log.i(TAG, "[StateSync] UPLOAD stateId=${state.id} | Complete | serverSaveId=${serverSave.id}")
                StateCloudResult.Success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "[StateSync] UPLOAD stateId=${state.id} | HTTP failed | status=${response.code()}, body=$errorBody")
                StateCloudResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[StateSync] UPLOAD stateId=${state.id} | Exception", e)
            StateCloudResult.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun downloadStateFromRomM(
        rommSaveId: Long,
        fileName: String,
        api: RomMApi,
        gameId: Long,
        platformSlug: String,
        emulatorId: String,
        slotNumber: Int,
        coreId: String? = null
    ): StateCloudResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "[StateSync] DOWNLOAD rommSaveId=$rommSaveId gameId=$gameId slot=$slotNumber")

        try {
            val response = api.downloadSaveContent(rommSaveId, fileName)
            if (!response.isSuccessful) {
                Log.e(TAG, "[StateSync] DOWNLOAD rommSaveId=$rommSaveId | HTTP failed | status=${response.code()}")
                return@withContext StateCloudResult.Error("Download failed: ${response.code()}")
            }

            val body = response.body()
            if (body == null) {
                Log.e(TAG, "[StateSync] DOWNLOAD rommSaveId=$rommSaveId | Empty response body")
                return@withContext StateCloudResult.Error("Empty response body")
            }

            val coreDir = getCoreDir(gameId, platformSlug, null, coreId)
            coreDir.mkdirs()

            val cachedFile = File(coreDir, fileName)
            body.byteStream().use { input ->
                cachedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val contentHash = calculateFileHash(cachedFile)
            val cachePath = "$platformSlug/$gameId/default/${coreId ?: "unknown"}/$fileName"
            val now = Instant.now()

            val entity = StateCacheEntity(
                gameId = gameId,
                platformSlug = platformSlug,
                emulatorId = emulatorId,
                slotNumber = slotNumber,
                channelName = null,
                cachedAt = now,
                stateSize = cachedFile.length(),
                cachePath = cachePath,
                screenshotPath = null,
                coreId = coreId,
                coreVersion = null,
                isLocked = false,
                note = null,
                rommSaveId = rommSaveId,
                syncStatus = StateCacheEntity.STATUS_SYNCED,
                serverUpdatedAt = now,
                lastUploadedHash = contentHash
            )

            stateCacheDao.upsert(entity)
            Log.i(TAG, "[StateSync] DOWNLOAD rommSaveId=$rommSaveId | Complete | cached at $cachePath")
            StateCloudResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "[StateSync] DOWNLOAD rommSaveId=$rommSaveId | Exception", e)
            StateCloudResult.Error(e.message ?: "Download failed")
        }
    }

    suspend fun checkServerStates(rommId: Long, api: RomMApi): List<RomMSave> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSavesByRom(rommId)
            if (!response.isSuccessful) {
                Log.w(TAG, "[StateSync] checkServerStates rommId=$rommId | HTTP failed | status=${response.code()}")
                return@withContext emptyList()
            }

            val allSaves = response.body() ?: emptyList()
            val states = allSaves.filter { save ->
                val ext = save.fileExtension?.lowercase() ?: File(save.fileName).extension.lowercase()
                isStateExtension(ext)
            }

            Log.d(TAG, "[StateSync] checkServerStates rommId=$rommId | Found ${states.size} states out of ${allSaves.size} saves")
            states
        } catch (e: Exception) {
            Log.e(TAG, "[StateSync] checkServerStates rommId=$rommId | Exception", e)
            emptyList()
        }
    }

    suspend fun getPendingUploads(): List<StateCacheEntity> =
        stateCacheDao.getPendingUploads()

    suspend fun getPendingUploadsByGame(gameId: Long): List<StateCacheEntity> =
        stateCacheDao.getPendingUploadsByGame(gameId)

    suspend fun markForUpload(stateId: Long) {
        stateCacheDao.updateSyncStatus(stateId, StateCacheEntity.STATUS_PENDING_UPLOAD)
    }

    suspend fun getByRommSaveId(rommSaveId: Long): StateCacheEntity? =
        stateCacheDao.getByRommSaveId(rommSaveId)

    suspend fun getByGameAndEmulator(gameId: Long, emulatorId: String): List<StateCacheEntity> =
        stateCacheDao.getByGameAndEmulator(gameId, emulatorId)

    private fun isStateExtension(ext: String): Boolean {
        val lower = ext.lowercase()
        return lower in STATE_EXTENSIONS ||
            lower.startsWith("state") ||
            lower == "ppst" ||
            lower == "dss" ||
            lower == "mln"
    }

    private fun calculateFileHash(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseTimestamp(timestamp: String): Instant? {
        return try {
            ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant()
        } catch (e: Exception) {
            try {
                Instant.parse(timestamp)
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to parse timestamp: $timestamp")
                null
            }
        }
    }

    fun parseSlotFromFileName(fileName: String): Int {
        val ext = File(fileName).extension.lowercase()

        if (ext == "auto" || fileName.endsWith(".state.auto")) return -1

        val slotMatch = SLOT_REGEX.find(fileName)
        if (slotMatch != null) {
            val slotStr = slotMatch.groupValues.getOrNull(1)
            return slotStr?.toIntOrNull() ?: 0
        }

        val underscoreMatch = UNDERSCORE_SLOT_REGEX.find(fileName)
        if (underscoreMatch != null) {
            return underscoreMatch.groupValues[1].toIntOrNull() ?: 0
        }

        return 0
    }

    suspend fun queueStateForUpload(
        stateCacheId: Long,
        gameId: Long,
        rommId: Long,
        emulatorId: String
    ): Boolean {
        val state = stateCacheDao.getById(stateCacheId) ?: return false

        val payload = SaveStatePayload(stateCacheId, emulatorId)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_STATE,
                priority = SyncPriority.SAVE_STATE,
                payloadJson = payload.toJson()
            )
        )
        Log.d(TAG, "[StateSync] QUEUED stateId=$stateCacheId gameId=$gameId")
        return true
    }

    suspend fun processPendingStateUploads(): Int = withContext(Dispatchers.IO) {
        val api = saveSyncRepository.get().getApi()
        if (api == null) {
            Log.d(TAG, "[StateSync] processPendingStateUploads | No API connection")
            return@withContext 0
        }

        val pending = pendingSyncQueueDao.getRetryableBySyncType(SyncType.SAVE_STATE)
        if (pending.isEmpty()) {
            return@withContext 0
        }

        Log.d(TAG, "[StateSync] processPendingStateUploads | Processing ${pending.size} pending uploads")

        var uploadedCount = 0
        for (item in pending) {
            val payload = SaveStatePayload.fromJson(item.payloadJson) ?: continue
            val state = stateCacheDao.getById(payload.stateCacheId)
            if (state == null) {
                Log.w(TAG, "[StateSync] UPLOAD itemId=${item.id} | State cache not found, removing from queue")
                pendingSyncQueueDao.deleteById(item.id)
                continue
            }

            val result = uploadStateToRomM(state, item.rommId, api)
            when (result) {
                is StateCloudResult.Success -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    uploadedCount++
                    Log.i(TAG, "[StateSync] UPLOADED stateId=${state.id} slot=${state.slotNumber}")
                }
                is StateCloudResult.AlreadySynced -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    Log.d(TAG, "[StateSync] UPLOAD stateId=${state.id} | Already synced, removed from queue")
                }
                is StateCloudResult.NoStateFound -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    Log.w(TAG, "[StateSync] UPLOAD stateId=${state.id} | Cache file not found, removed from queue")
                }
                is StateCloudResult.Error -> {
                    pendingSyncQueueDao.markFailed(item.id, result.message)
                    Log.w(TAG, "[StateSync] UPLOAD FAILED stateId=${state.id} | ${result.message} | retry=${item.retryCount + 1}")
                }
                else -> {
                    Log.w(TAG, "[StateSync] UPLOAD stateId=${state.id} | Unexpected result: $result")
                }
            }
        }

        Log.d(TAG, "[StateSync] processPendingStateUploads | Completed | uploaded=$uploadedCount")
        uploadedCount
    }
}
