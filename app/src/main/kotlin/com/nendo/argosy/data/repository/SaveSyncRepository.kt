package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.TitleIdExtractor
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSaveSyncDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.PendingSaveSyncEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SaveSyncRepository"
private const val DEFAULT_SAVE_NAME = "argosy-latest"
private val TIMESTAMP_ONLY_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}[_-]\d{2}[_-]\d{2}[_-]\d{2}$""")

sealed class SaveSyncResult {
    data object Success : SaveSyncResult()
    data class Conflict(
        val gameId: Long,
        val localTimestamp: Instant,
        val serverTimestamp: Instant
    ) : SaveSyncResult()
    data class Error(val message: String) : SaveSyncResult()
    data object NoSaveFound : SaveSyncResult()
    data object NotConfigured : SaveSyncResult()
}

enum class SyncDirection { UPLOAD, DOWNLOAD }
enum class SyncStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

data class SyncOperation(
    val gameId: Long,
    val gameName: String,
    val coverPath: String?,
    val direction: SyncDirection,
    val status: SyncStatus,
    val progress: Float = 0f,
    val error: String? = null
)

data class SyncQueueState(
    val operations: List<SyncOperation> = emptyList(),
    val isActive: Boolean = false,
    val currentOperation: SyncOperation? = null
) {
    val hasOperations: Boolean get() = operations.isNotEmpty()
    val pendingCount: Int get() = operations.count { it.status == SyncStatus.PENDING }
    val completedCount: Int get() = operations.count { it.status == SyncStatus.COMPLETED }
    val inProgressCount: Int get() = operations.count { it.status == SyncStatus.IN_PROGRESS }
}

@Singleton
class SaveSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSaveSyncDao: PendingSaveSyncDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val gameDao: GameDao,
    private val retroArchConfigParser: RetroArchConfigParser,
    private val titleIdExtractor: TitleIdExtractor,
    private val saveArchiver: SaveArchiver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>
) {
    private var api: RomMApi? = null

    private val _syncQueueState = MutableStateFlow(SyncQueueState())
    val syncQueueState: StateFlow<SyncQueueState> = _syncQueueState.asStateFlow()

    fun setApi(api: RomMApi?) {
        this.api = api
    }

    private fun updateSyncQueue(transform: (SyncQueueState) -> SyncQueueState) {
        _syncQueueState.value = transform(_syncQueueState.value)
    }

    private fun addOperation(operation: SyncOperation) {
        updateSyncQueue { state ->
            val existingIndex = state.operations.indexOfFirst { it.gameId == operation.gameId }
            val updatedOps = if (existingIndex >= 0) {
                state.operations.toMutableList().apply { set(existingIndex, operation) }
            } else {
                state.operations + operation
            }
            state.copy(operations = updatedOps, isActive = true)
        }
    }

    private fun updateOperation(gameId: Long, transform: (SyncOperation) -> SyncOperation) {
        updateSyncQueue { state ->
            val updatedOps = state.operations.map { op ->
                if (op.gameId == gameId) transform(op) else op
            }
            val current = updatedOps.find { it.status == SyncStatus.IN_PROGRESS }
            state.copy(operations = updatedOps, currentOperation = current)
        }
    }

    private fun completeOperation(gameId: Long, error: String? = null) {
        updateSyncQueue { state ->
            val updatedOps = state.operations.map { op ->
                if (op.gameId == gameId) {
                    op.copy(
                        status = if (error == null) SyncStatus.COMPLETED else SyncStatus.FAILED,
                        error = error,
                        progress = if (error == null) 1f else op.progress
                    )
                } else op
            }
            val remaining = updatedOps.filter { it.status != SyncStatus.COMPLETED && it.status != SyncStatus.FAILED }
            val current = updatedOps.find { it.status == SyncStatus.IN_PROGRESS }
            state.copy(
                operations = updatedOps,
                currentOperation = current,
                isActive = remaining.isNotEmpty()
            )
        }
    }

    fun clearCompletedOperations() {
        updateSyncQueue { state ->
            val remaining = state.operations.filter {
                it.status != SyncStatus.COMPLETED && it.status != SyncStatus.FAILED
            }
            state.copy(operations = remaining, isActive = remaining.isNotEmpty())
        }
    }

    private suspend fun isFolderSaveSyncEnabled(): Boolean {
        val prefs = userPreferencesRepository.preferences.first()
        return prefs.saveSyncEnabled && prefs.experimentalFolderSaveSync
    }

    fun observeNewSavesCount(): Flow<Int> = saveSyncDao.observeNewSavesCount()

    fun observePendingCount(): Flow<Int> = pendingSaveSyncDao.observePendingCount()

    suspend fun discoverSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String? = null,
        cachedTitleId: String? = null,
        coreName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId) ?: return@withContext null

        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        val isRetroArch = emulatorId == "retroarch" || emulatorId == "retroarch_64"

        if (userConfig?.isUserOverride == true && !isRetroArch) {
            if (romPath != null) {
                val savePath = findSaveByRomName(userConfig.savePathPattern, romPath, config.saveExtensions)
                if (savePath != null) return@withContext savePath
            }
            return@withContext findSaveInPath(userConfig.savePathPattern, gameTitle, config.saveExtensions)
        }

        if (config.usesFolderBasedSaves && romPath != null) {
            if (!isFolderSaveSyncEnabled()) {
                return@withContext null
            }
            return@withContext discoverFolderSavePath(config, platformSlug, romPath, cachedTitleId)
        }

        val basePathOverride = if (isRetroArch && userConfig?.isUserOverride == true) {
            userConfig.savePathPattern
        } else null

        val paths = if (isRetroArch) {
            val packageName = if (emulatorId == "retroarch_64") "com.retroarch.aarch64" else "com.retroarch"
            val contentDir = romPath?.let { File(it).parent }
            if (coreName != null) {
                Logger.debug(TAG, "discoverSavePath: RetroArch using known core=$coreName (baseOverride=$basePathOverride)")
                retroArchConfigParser.resolveSavePaths(packageName, platformSlug, coreName, contentDir, basePathOverride)
            } else {
                val corePatterns = EmulatorRegistry.getRetroArchCorePatterns()[platformSlug] ?: emptyList()
                Logger.debug(TAG, "discoverSavePath: RetroArch trying all cores=$corePatterns (baseOverride=$basePathOverride)")
                corePatterns.flatMap { core ->
                    retroArchConfigParser.resolveSavePaths(packageName, platformSlug, core, contentDir, basePathOverride)
                } + retroArchConfigParser.resolveSavePaths(packageName, platformSlug, null, contentDir, basePathOverride)
            }
        } else {
            SavePathRegistry.resolvePath(config, platformSlug)
        }

        Logger.debug(TAG, "discoverSavePath: searching ${paths.size} paths for '$gameTitle' (romPath=$romPath)")

        if (romPath != null) {
            for (basePath in paths) {
                val savePath = findSaveByRomName(basePath, romPath, config.saveExtensions)
                if (savePath != null) {
                    Logger.debug(TAG, "discoverSavePath: ROM-based match found at $savePath")
                    emulatorSaveConfigDao.upsert(
                        EmulatorSaveConfigEntity(
                            emulatorId = emulatorId,
                            savePathPattern = File(savePath).parent ?: basePath,
                            isAutoDetected = true,
                            lastVerifiedAt = Instant.now()
                        )
                    )
                    return@withContext savePath
                }
            }
            Logger.debug(TAG, "discoverSavePath: ROM-based lookup found nothing, trying title match")
        }

        for (basePath in paths) {
            val saveFile = findSaveInPath(basePath, gameTitle, config.saveExtensions)
            if (saveFile != null) {
                Logger.debug(TAG, "discoverSavePath: found save at $saveFile")
                emulatorSaveConfigDao.upsert(
                    EmulatorSaveConfigEntity(
                        emulatorId = emulatorId,
                        savePathPattern = basePath,
                        isAutoDetected = true,
                        lastVerifiedAt = Instant.now()
                    )
                )
                return@withContext saveFile
            }
        }

        Logger.verbose(TAG) {
            "discoverSavePath: FAILED - no save found for '$gameTitle' (romPath=$romPath) " +
                "after checking ${paths.size} paths"
        }
        null
    }

    private fun discoverFolderSavePath(
        config: SavePathConfig,
        platformSlug: String,
        romPath: String,
        cachedTitleId: String? = null
    ): String? {
        val romFile = File(romPath)
        val titleId = cachedTitleId
            ?: titleIdExtractor.extractTitleId(romFile, platformSlug)
            ?: return null

        Logger.debug(TAG, "Using titleId: $titleId (cached: ${cachedTitleId != null})")

        for (basePath in config.defaultPaths) {
            val saveFolder = findSaveFolderByTitleId(basePath, titleId, platformSlug)
            if (saveFolder != null) return saveFolder
        }
        return null
    }

    private fun findSaveFolderByTitleId(
        basePath: String,
        titleId: String,
        platformSlug: String
    ): String? {
        val baseDir = File(basePath)
        if (!baseDir.exists()) return null

        when (platformSlug) {
            "vita", "psvita" -> {
                val saveFolder = File(baseDir, titleId)
                if (saveFolder.exists() && saveFolder.isDirectory) {
                    return saveFolder.absolutePath
                }
            }
            "switch" -> {
                baseDir.listFiles()?.forEach { userFolder ->
                    if (userFolder.isDirectory) {
                        val saveFolder = File(userFolder, titleId)
                        if (saveFolder.exists() && saveFolder.isDirectory) {
                            return saveFolder.absolutePath
                        }
                        userFolder.listFiles()?.forEach { profileFolder ->
                            if (profileFolder.isDirectory) {
                                val nestedSave = File(profileFolder, titleId)
                                if (nestedSave.exists() && nestedSave.isDirectory) {
                                    return nestedSave.absolutePath
                                }
                            }
                        }
                    }
                }
            }
            "3ds" -> {
                baseDir.listFiles()?.forEach { folder1 ->
                    if (folder1.isDirectory) {
                        folder1.listFiles()?.forEach { folder2 ->
                            if (folder2.isDirectory) {
                                val titleFolder = File(folder2, "title/00040000/$titleId/data")
                                if (titleFolder.exists() && titleFolder.isDirectory) {
                                    return titleFolder.absolutePath
                                }
                            }
                        }
                    }
                }
            }
            "psp" -> {
                baseDir.listFiles()?.forEach { folder ->
                    if (folder.isDirectory && folder.name.startsWith(titleId)) {
                        return folder.absolutePath
                    }
                }
            }
            "wiiu" -> {
                val saveFolder = File(baseDir, titleId)
                if (saveFolder.exists() && saveFolder.isDirectory) {
                    return saveFolder.absolutePath
                }
            }
        }
        return null
    }

    private fun findActiveProfileFolder(basePath: String, platformSlug: String): String {
        if (platformSlug != "switch") {
            return basePath
        }

        val baseDir = File(basePath)
        if (!baseDir.exists()) {
            val defaultPath = "$basePath/0000000000000001/0000000000000001"
            File(defaultPath).mkdirs()
            return defaultPath
        }

        var mostRecentPath: String? = null
        var mostRecentTime = 0L

        baseDir.listFiles()?.forEach { userFolder ->
            if (!userFolder.isDirectory) return@forEach
            if (!isValidSwitchHexId(userFolder.name)) return@forEach

            userFolder.listFiles()?.forEach { profileFolder ->
                if (!profileFolder.isDirectory) return@forEach
                if (!isValidSwitchHexId(profileFolder.name)) return@forEach

                val modified = profileFolder.lastModified()
                if (modified > mostRecentTime) {
                    mostRecentTime = modified
                    mostRecentPath = profileFolder.absolutePath
                }
            }
        }

        return mostRecentPath
            ?: "$basePath/0000000000000001/0000000000000001".also { File(it).mkdirs() }
    }

    private fun isValidSwitchHexId(name: String): Boolean {
        return name.length == 16 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun findSaveByRomName(
        basePath: String,
        romPath: String,
        extensions: List<String>
    ): String? {
        val dir = File(basePath)
        if (!dir.exists() || !dir.isDirectory) {
            Logger.verbose(TAG) { "findSaveByRomName: dir does not exist: $basePath" }
            return null
        }

        val romFile = File(romPath)
        val romName = romFile.nameWithoutExtension
        val isZipContainer = romFile.extension.equals("zip", ignoreCase = true)

        Logger.verbose(TAG) {
            "findSaveByRomName: romPath=${romFile.name}, romName=$romName, " +
                "isZip=$isZipContainer, extensions=$extensions, searchDir=$basePath"
        }

        if (isZipContainer) {
            Logger.verbose(TAG) {
                "findSaveByRomName: WARNING - ROM is in ZIP container, " +
                    "save filename may differ from container name '$romName'"
            }
        }

        for (ext in extensions) {
            if (ext == "*") continue
            val saveFile = File(dir, "$romName.$ext")
            Logger.verbose(TAG) { "findSaveByRomName: checking ${saveFile.name} -> exists=${saveFile.exists()}" }
            if (saveFile.exists() && saveFile.isFile) {
                Logger.debug(TAG, "findSaveByRomName: found ${saveFile.absolutePath}")
                return saveFile.absolutePath
            }
        }

        if (Logger.isVerbose) {
            val existingFiles = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
            Logger.verbose(TAG) {
                "findSaveByRomName: no match for '$romName' in $basePath, " +
                    "existing files (${existingFiles.size}): ${existingFiles.take(10).joinToString()}" +
                    if (existingFiles.size > 10) "..." else ""
            }
        }

        return null
    }

    private fun findSaveInPath(
        basePath: String,
        gameTitle: String,
        extensions: List<String> = listOf("*")
    ): String? {
        val dir = File(basePath)
        if (!dir.exists() || !dir.isDirectory) return null

        val sanitizedTitle = sanitizeFileName(gameTitle).lowercase()
        val files = dir.listFiles() ?: return null

        return files.firstOrNull { file ->
            val name = file.nameWithoutExtension.lowercase()
            val ext = file.extension.lowercase()
            val matchesName = name == sanitizedTitle ||
                name.contains(sanitizedTitle) ||
                sanitizedTitle.contains(name)
            val matchesExt = extensions.contains("*") || extensions.contains(ext)
            file.isFile && matchesName && matchesExt
        }?.absolutePath
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?
    ): String? {
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null

        if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            return constructRetroArchSavePath(emulatorId, gameTitle, platformSlug, romPath)
        }

        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        val baseDir = if (userConfig?.isUserOverride == true) {
            val userPath = userConfig.savePathPattern
            val userDir = File(userPath)
            if (userDir.exists() || userDir.mkdirs()) userPath else null
        } else {
            null
        } ?: run {
            val resolvedPaths = SavePathRegistry.resolvePath(config, platformSlug)
            resolvedPaths.firstOrNull { File(it).exists() }
                ?: resolvedPaths.firstOrNull()
        } ?: return null

        val extension = config.saveExtensions.firstOrNull { it != "*" } ?: "sav"
        val sanitizedName = sanitizeFileName(gameTitle)
        val fileName = "$sanitizedName.$extension"

        return "$baseDir/$fileName"
    }

    private fun constructRetroArchSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?
    ): String? {
        val packageName = when (emulatorId) {
            "retroarch_64" -> "com.retroarch.aarch64"
            else -> "com.retroarch"
        }

        val raConfig = retroArchConfigParser.parse(packageName)
        val coreName = SavePathRegistry.getRetroArchCore(platformSlug) ?: return null
        val saveConfig = SavePathRegistry.getConfig(emulatorId) ?: return null
        val extension = saveConfig.saveExtensions.firstOrNull() ?: "srm"

        val baseDir = when {
            raConfig?.savefilesInContentDir == true && romPath != null -> {
                File(romPath).parent
            }
            raConfig?.savefileDirectory != null -> {
                if (raConfig.sortByCore) {
                    "${raConfig.savefileDirectory}/$coreName"
                } else {
                    raConfig.savefileDirectory
                }
            }
            else -> {
                val defaultPaths = SavePathRegistry.resolvePath(saveConfig, platformSlug)
                defaultPaths.firstOrNull { File(it).exists() }
                    ?: defaultPaths.firstOrNull()
            }
        } ?: return null

        val fileName = buildRetroArchFileName(gameTitle, romPath, extension)
        return "$baseDir/$fileName"
    }

    private fun buildRetroArchFileName(
        gameTitle: String,
        romPath: String?,
        extension: String
    ): String {
        if (romPath != null) {
            val romFile = File(romPath)
            val romName = romFile.nameWithoutExtension
            val isZipContainer = romFile.extension.equals("zip", ignoreCase = true)
            val result = "$romName.$extension"

            Logger.verbose(TAG) {
                "buildRetroArchFileName: romPath=${romFile.name}, derived=$result, isZip=$isZipContainer"
            }

            if (isZipContainer) {
                Logger.verbose(TAG) {
                    "buildRetroArchFileName: WARNING - using ZIP container name '$romName', " +
                        "but RetroArch may use inner ROM filename for saves"
                }
            }

            return result
        }

        val sanitized = gameTitle
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val result = "$sanitized.$extension"
        Logger.verbose(TAG) { "buildRetroArchFileName: no romPath, using sanitized title -> $result" }
        return result
    }

    suspend fun constructSavePathWithFileName(
        emulatorId: String,
        platformSlug: String,
        romPath: String?,
        serverFileName: String
    ): String? {
        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverFileName.endsWith(".zip", ignoreCase = true)
        val isSwitchEmulator = emulatorId in SWITCH_EMULATOR_IDS

        if (isFolderBased && isSwitchEmulator && config != null) {
            val titleId = serverFileName.removeSuffix(".zip").removeSuffix(".ZIP")
            if (isValidSwitchHexId(titleId)) {
                val basePath = config.defaultPaths.firstOrNull { File(it).exists() }
                    ?: config.defaultPaths.firstOrNull()
                    ?: return null
                val profileFolder = findActiveProfileFolder(basePath, platformSlug)
                return "$profileFolder/$titleId"
            }
        }

        val baseDir = getSaveDirectory(emulatorId, platformSlug, romPath) ?: return null
        return "$baseDir/$serverFileName"
    }

    private suspend fun getSaveDirectory(
        emulatorId: String,
        platformSlug: String,
        romPath: String?
    ): String? {
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null

        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        if (userConfig?.isUserOverride == true) {
            val userPath = userConfig.savePathPattern
            val userDir = File(userPath)
            if (userDir.exists() || userDir.mkdirs()) {
                return userPath
            }
        }

        if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            return getRetroArchSaveDirectory(emulatorId, platformSlug, romPath)
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, platformSlug)
        return resolvedPaths.firstOrNull { File(it).exists() }
            ?: resolvedPaths.firstOrNull()
    }

    private fun getRetroArchSaveDirectory(
        emulatorId: String,
        platformSlug: String,
        romPath: String?
    ): String? {
        val packageName = when (emulatorId) {
            "retroarch_64" -> "com.retroarch.aarch64"
            else -> "com.retroarch"
        }

        val raConfig = retroArchConfigParser.parse(packageName)
        val coreName = SavePathRegistry.getRetroArchCore(platformSlug)
        val saveConfig = SavePathRegistry.getConfig(emulatorId) ?: return null

        return when {
            raConfig?.savefilesInContentDir == true && romPath != null -> {
                File(romPath).parent
            }
            raConfig?.savefileDirectory != null -> {
                if (raConfig.sortByCore && coreName != null) {
                    "${raConfig.savefileDirectory}/$coreName"
                } else {
                    raConfig.savefileDirectory
                }
            }
            else -> {
                val defaultPaths = SavePathRegistry.resolvePath(saveConfig, platformSlug)
                defaultPaths.firstOrNull { File(it).exists() }
                    ?: defaultPaths.firstOrNull()
            }
        }
    }

    suspend fun getSyncStatus(gameId: Long, emulatorId: String): SaveSyncEntity? {
        return saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
    }

    suspend fun checkForAllServerUpdates(): List<SaveSyncEntity> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext emptyList()
        val downloadedGames = gameDao.getGamesWithLocalPath().filter { it.rommId != null }
        val platformIds = downloadedGames.map { it.platformId }.distinct()

        val allUpdates = mutableListOf<SaveSyncEntity>()
        for (platformId in platformIds) {
            allUpdates.addAll(checkForServerUpdates(platformId))
        }
        allUpdates
    }

    suspend fun checkForServerUpdates(platformId: Long): List<SaveSyncEntity> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext emptyList()

        val response = try {
            api.getSavesByPlatform(platformId)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        if (!response.isSuccessful) return@withContext emptyList()

        val serverSaves = response.body() ?: return@withContext emptyList()
        val updatedEntities = mutableListOf<SaveSyncEntity>()

        val downloadedGames = gameDao.getGamesWithLocalPath()
            .filter { it.rommId != null }

        for (serverSave in serverSaves) {
            val game = downloadedGames.find { it.rommId == serverSave.romId } ?: continue
            val emulatorId = serverSave.emulator ?: "default"
            val channelName = parseServerChannelName(serverSave.fileName)

            // Skip timestamp saves - they're not synced
            if (channelName == null) continue

            val existing = saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, channelName)

            val serverTime = parseTimestamp(serverSave.updatedAt)

            if (existing == null || serverTime.isAfter(existing.serverUpdatedAt)) {
                val entity = SaveSyncEntity(
                    id = existing?.id ?: 0,
                    gameId = game.id,
                    rommId = game.rommId!!,
                    emulatorId = emulatorId,
                    channelName = channelName,
                    rommSaveId = serverSave.id,
                    localSavePath = existing?.localSavePath,
                    localUpdatedAt = existing?.localUpdatedAt,
                    serverUpdatedAt = serverTime,
                    lastSyncedAt = existing?.lastSyncedAt,
                    syncStatus = determineSyncStatus(existing?.localUpdatedAt, serverTime)
                )
                saveSyncDao.upsert(entity)
                if (entity.syncStatus == SaveSyncEntity.STATUS_SERVER_NEWER) {
                    updatedEntities.add(entity)
                }
            }
        }

        updatedEntities
    }

    suspend fun checkSavesForGame(gameId: Long, rommId: Long): List<RomMSave> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext emptyList()

        val response = try {
            api.getSavesByRom(rommId)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        response.body() ?: emptyList()
    }

    private fun determineSyncStatus(localTime: Instant?, serverTime: Instant): String {
        if (localTime == null) return SaveSyncEntity.STATUS_SERVER_NEWER
        return when {
            serverTime.isAfter(localTime) -> SaveSyncEntity.STATUS_SERVER_NEWER
            localTime.isAfter(serverTime) -> SaveSyncEntity.STATUS_LOCAL_NEWER
            else -> SaveSyncEntity.STATUS_SYNCED
        }
    }

    private fun isTimestampSaveName(baseName: String): Boolean {
        return TIMESTAMP_ONLY_PATTERN.matches(baseName)
    }

    private fun parseServerChannelName(fileName: String): String? {
        val baseName = File(fileName).nameWithoutExtension
        if (isTimestampSaveName(baseName)) return null
        return baseName
    }

    suspend fun uploadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "uploadSave: gameId=$gameId, emulator=$emulatorId, channel=$channelName")
        val api = this@SaveSyncRepository.api ?: return@withContext SaveSyncResult.NotConfigured

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        }

        val game = gameDao.getById(gameId) ?: return@withContext SaveSyncResult.Error("Game not found")
        val rommId = game.rommId ?: return@withContext SaveSyncResult.NotConfigured

        val localPath = syncEntity?.localSavePath
            ?: discoverSavePath(emulatorId, game.title, game.platformSlug, game.localPath)
            ?: return@withContext SaveSyncResult.NoSaveFound

        val saveLocation = File(localPath)
        if (!saveLocation.exists()) return@withContext SaveSyncResult.NoSaveFound

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isFolderBased = config?.usesFolderBasedSaves == true && saveLocation.isDirectory

        if (isFolderBased && !isFolderSaveSyncEnabled()) {
            return@withContext SaveSyncResult.NotConfigured
        }

        val localModified = Instant.ofEpochMilli(saveLocation.lastModified())
        Logger.debug(TAG, "uploadSave: localModified=$localModified (epochMillis=${saveLocation.lastModified()})")

        var tempZipFile: File? = null

        try {
            val fileToUpload = if (isFolderBased) {
                tempZipFile = File(context.cacheDir, "${saveLocation.name}.zip")
                if (!saveArchiver.zipFolder(saveLocation, tempZipFile)) {
                    return@withContext SaveSyncResult.Error("Failed to zip save folder")
                }
                tempZipFile
            } else {
                saveLocation
            }

            val uploadFileName = if (channelName != null) {
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$channelName.$ext" else channelName
            } else {
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$DEFAULT_SAVE_NAME.$ext" else DEFAULT_SAVE_NAME
            }

            val romFile = game.localPath?.let { File(it) }
            val romBaseName = romFile?.nameWithoutExtension
            val isZipContainer = romFile?.extension?.equals("zip", ignoreCase = true) == true

            Logger.verbose(TAG) {
                "uploadSave: romPath=${romFile?.name}, romBaseName=$romBaseName, isZip=$isZipContainer"
            }

            if (isZipContainer) {
                Logger.verbose(TAG) {
                    "uploadSave: WARNING - ROM is in ZIP container, romBaseName '$romBaseName' " +
                        "may not match actual save filename used by emulator"
                }
            }

            val serverSaves = checkSavesForGame(gameId, rommId)

            Logger.verbose(TAG) {
                val saveNames = serverSaves.map { it.fileName }
                "uploadSave: server has ${serverSaves.size} saves: $saveNames"
            }

            val latestServerSave = if (channelName != null) {
                serverSaves.find { File(it.fileName).nameWithoutExtension.equals(channelName, ignoreCase = true) }
            } else {
                serverSaves.find { isLatestSaveFileName(it.fileName, romBaseName) }
            }

            val existingServerSave = serverSaves.find { serverSave ->
                val baseName = File(serverSave.fileName).nameWithoutExtension
                if (channelName != null) {
                    baseName.equals(channelName, ignoreCase = true)
                } else {
                    baseName.equals(DEFAULT_SAVE_NAME, ignoreCase = true) ||
                        romBaseName != null && baseName.equals(romBaseName, ignoreCase = true)
                }
            }

            Logger.verbose(TAG) {
                "uploadSave: latestServerSave=${latestServerSave?.fileName}, " +
                    "existingServerSave=${existingServerSave?.fileName}, " +
                    "matching against romBaseName='$romBaseName'"
            }

            if (channelName == null && latestServerSave != null) {
                val serverTime = parseTimestamp(latestServerSave.updatedAt)
                Logger.debug(TAG, "uploadSave: comparing local=$localModified vs server=$serverTime (raw=${latestServerSave.updatedAt})")
                if (serverTime.isAfter(localModified)) {
                    Logger.debug(TAG, "uploadSave: conflict - server is after local, blocking upload")
                    return@withContext SaveSyncResult.Conflict(gameId, localModified, serverTime)
                }
                Logger.debug(TAG, "uploadSave: local is newer or equal, proceeding with upload")
            }

            val serverSaveIdToUpdate = syncEntity?.rommSaveId ?: existingServerSave?.id

            val requestBody = fileToUpload.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, requestBody)

            var response = if (serverSaveIdToUpdate != null) {
                api.updateSave(serverSaveIdToUpdate, filePart)
            } else {
                api.uploadSave(rommId, emulatorId, filePart)
            }

            if (!response.isSuccessful && serverSaveIdToUpdate != null) {
                Logger.debug(TAG, "uploadSave: update failed with ${response.code()}, retrying as new upload")
                val retryRequestBody = fileToUpload.asRequestBody("application/octet-stream".toMediaType())
                val retryFilePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, retryRequestBody)
                response = api.uploadSave(rommId, emulatorId, retryFilePart)
            }

            if (response.isSuccessful) {
                val serverSave = response.body()!!
                Logger.debug(TAG, "uploadSave: success, serverSaveId=${serverSave.id}")
                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = syncEntity?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        channelName = channelName,
                        rommSaveId = serverSave.id,
                        localSavePath = localPath,
                        localUpdatedAt = localModified,
                        serverUpdatedAt = parseTimestamp(serverSave.updatedAt),
                        lastSyncedAt = Instant.now(),
                        syncStatus = SaveSyncEntity.STATUS_SYNCED
                    )
                )
                SaveSyncResult.Success
            } else {
                Logger.error(TAG, "uploadSave failed: ${response.code()}")
                SaveSyncResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "uploadSave exception", e)
            SaveSyncResult.Error(e.message ?: "Upload failed")
        } finally {
            tempZipFile?.delete()
        }
    }

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "downloadSave: gameId=$gameId, emulator=$emulatorId, channel=$channelName")
        val api = this@SaveSyncRepository.api
            ?: return@withContext SaveSyncResult.NotConfigured

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        } ?: return@withContext SaveSyncResult.Error("No save tracking found")

        val saveId = syncEntity.rommSaveId
            ?: return@withContext SaveSyncResult.Error("No server save ID")

        val game = gameDao.getById(gameId)
            ?: return@withContext SaveSyncResult.Error("Game not found")

        val serverSave = try {
            api.getSave(saveId).body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSave: getSave failed", e)
            return@withContext SaveSyncResult.Error("Failed to get save info: ${e.message}")
        } ?: return@withContext SaveSyncResult.Error("Save not found on server")

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true)
        val isSwitchEmulator = emulatorId in SWITCH_EMULATOR_IDS

        if (isFolderBased && !isFolderSaveSyncEnabled()) {
            return@withContext SaveSyncResult.NotConfigured
        }

        val preDownloadTargetPath = if (isFolderBased) {
            if (isSwitchEmulator) {
                syncEntity.localSavePath
            } else {
                syncEntity.localSavePath
                    ?: constructFolderSavePath(emulatorId, game.platformSlug, game.localPath)
            }
        } else {
            syncEntity.localSavePath
                ?: discoverSavePath(emulatorId, game.title, game.platformSlug, game.localPath)
                ?: constructSavePathWithFileName(emulatorId, platformSlug = game.platformSlug, romPath = game.localPath, serverFileName = serverSave.fileName)
        }

        if (!isSwitchEmulator && !isFolderBased && preDownloadTargetPath == null) {
            return@withContext SaveSyncResult.Error("Cannot determine save path")
        }

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath
                ?: return@withContext SaveSyncResult.Error("No download path available")

            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Logger.error(TAG, "downloadSave failed: ${response.code()}")
                return@withContext SaveSyncResult.Error("Download failed: ${response.code()}")
            }

            val targetPath: String

            if (isFolderBased) {
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                response.body()?.byteStream()?.use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                targetPath = if (isSwitchEmulator && config != null) {
                    preDownloadTargetPath
                        ?: resolveSwitchSaveTargetPath(tempZipFile, config)
                        ?: return@withContext SaveSyncResult.Error("Cannot determine save path from ZIP")
                } else {
                    preDownloadTargetPath
                        ?: return@withContext SaveSyncResult.Error("Cannot determine save path")
                }

                val existingTarget = File(targetPath)
                if (existingTarget.exists() && !skipBackup) {
                    try {
                        saveCacheManager.get().cacheCurrentSave(gameId, emulatorId, targetPath)
                        Logger.debug(TAG, "Cached existing save before download for game $gameId")
                    } catch (e: Exception) {
                        Logger.warn(TAG, "Failed to cache existing save before download", e)
                    }
                }

                val targetFolder = File(targetPath)
                targetFolder.mkdirs()

                if (!saveArchiver.unzipSingleFolder(tempZipFile, targetFolder)) {
                    return@withContext SaveSyncResult.Error("Failed to unzip save")
                }
            } else {
                targetPath = preDownloadTargetPath!!

                val existingTarget = File(targetPath)
                if (existingTarget.exists() && !skipBackup) {
                    try {
                        saveCacheManager.get().cacheCurrentSave(gameId, emulatorId, targetPath)
                        Logger.debug(TAG, "Cached existing save before download for game $gameId")
                    } catch (e: Exception) {
                        Logger.warn(TAG, "Failed to cache existing save before download", e)
                    }
                }
                val targetFile = File(targetPath)
                targetFile.parentFile?.mkdirs()

                response.body()?.byteStream()?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            saveSyncDao.upsert(
                syncEntity.copy(
                    localSavePath = targetPath,
                    localUpdatedAt = Instant.now(),
                    lastSyncedAt = Instant.now(),
                    syncStatus = SaveSyncEntity.STATUS_SYNCED
                )
            )

            val effectiveChannelName = channelName ?: syncEntity.channelName
            val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
            val isLatestSave = effectiveChannelName == null ||
                effectiveChannelName.equals(DEFAULT_SAVE_NAME, ignoreCase = true) ||
                romBaseName != null && effectiveChannelName.equals(romBaseName, ignoreCase = true)

            val cacheChannelName = if (isLatestSave) null else effectiveChannelName
            val cacheIsLocked = !isLatestSave

            try {
                saveCacheManager.get().cacheCurrentSave(
                    gameId = gameId,
                    emulatorId = emulatorId,
                    savePath = targetPath,
                    channelName = cacheChannelName,
                    isLocked = cacheIsLocked
                )
            } catch (e: Exception) {
                Logger.error(TAG, "Cache creation failed", e)
            }

            Logger.debug(TAG, "downloadSave: success, saved to $targetPath")
            SaveSyncResult.Success
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSave exception", e)
            SaveSyncResult.Error(e.message ?: "Download failed")
        } finally {
            tempZipFile?.delete()
        }
    }

    suspend fun downloadSaveById(
        serverSaveId: Long,
        targetPath: String,
        emulatorId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext false

        val serverSave = try {
            api.getSave(serverSaveId).body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveById: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true)
        val isSwitchEmulator = emulatorId in SWITCH_EMULATOR_IDS

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath ?: return@withContext false

            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Logger.error(TAG, "downloadSaveById failed: ${response.code()}")
                return@withContext false
            }

            if (isFolderBased) {
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                response.body()?.byteStream()?.use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val resolvedTargetPath = if (isSwitchEmulator && config != null) {
                    resolveSwitchSaveTargetPath(tempZipFile, config) ?: targetPath
                } else {
                    targetPath
                }

                val targetFolder = File(resolvedTargetPath)
                targetFolder.mkdirs()

                if (!saveArchiver.unzipSingleFolder(tempZipFile, targetFolder)) {
                    return@withContext false
                }
            } else {
                val targetFile = File(targetPath)
                targetFile.parentFile?.mkdirs()

                response.body()?.byteStream()?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            true
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveById exception", e)
            false
        } finally {
            tempZipFile?.delete()
        }
    }

    private fun resolveSwitchSaveTargetPath(zipFile: File, config: SavePathConfig): String? {
        val titleId = saveArchiver.peekRootFolderName(zipFile)
        if (titleId == null || !isValidSwitchHexId(titleId)) {
            Logger.debug(TAG, "resolveSwitchSaveTargetPath: invalid titleId from ZIP: $titleId")
            return null
        }

        val basePath = config.defaultPaths.firstOrNull { File(it).exists() }
            ?: config.defaultPaths.firstOrNull()
            ?: return null

        val profileFolder = findActiveProfileFolder(basePath, "switch")
        val targetPath = "$profileFolder/$titleId"

        Logger.debug(TAG, "resolveSwitchSaveTargetPath: resolved to $targetPath (titleId=$titleId)")
        return targetPath
    }

    private fun constructFolderSavePath(
        emulatorId: String,
        platformSlug: String,
        romPath: String?
    ): String? {
        if (romPath == null) return null

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId) ?: return null
        if (!config.usesFolderBasedSaves) return null

        val romFile = File(romPath)
        val titleId = titleIdExtractor.extractTitleId(romFile, platformSlug) ?: return null

        val baseDir = config.defaultPaths.firstOrNull { File(it).exists() }
            ?: config.defaultPaths.firstOrNull()
            ?: return null

        return when (platformSlug) {
            "vita", "psvita" -> "$baseDir/$titleId"
            "switch" -> {
                val profileFolder = findActiveProfileFolder(baseDir, platformSlug)
                "$profileFolder/$titleId"
            }
            "3ds" -> {
                val nintendo3dsDir = File(baseDir)
                val userFolders = nintendo3dsDir.listFiles()?.filter { it.isDirectory }
                val folder1 = userFolders?.firstOrNull()
                val folder2 = folder1?.listFiles()?.firstOrNull { it.isDirectory }
                if (folder2 != null) {
                    "${folder2.absolutePath}/title/00040000/$titleId/data"
                } else {
                    null
                }
            }
            "psp" -> "$baseDir/$titleId"
            else -> null
        }
    }

    suspend fun queueUpload(gameId: Long, emulatorId: String, localPath: String) {
        val game = gameDao.getById(gameId) ?: return
        val rommId = game.rommId ?: return

        pendingSaveSyncDao.deleteByGameAndEmulator(gameId, emulatorId)
        pendingSaveSyncDao.insert(
            PendingSaveSyncEntity(
                gameId = gameId,
                rommId = rommId,
                emulatorId = emulatorId,
                localSavePath = localPath,
                action = PendingSaveSyncEntity.ACTION_UPLOAD
            )
        )
    }

    suspend fun processPendingUploads(): Int = withContext(Dispatchers.IO) {
        val pending = pendingSaveSyncDao.getRetryable()
        if (pending.isEmpty()) {
            return@withContext 0
        }
        Logger.info(TAG, "Processing ${pending.size} pending save uploads")

        for (item in pending) {
            val game = gameDao.getById(item.gameId) ?: continue
            addOperation(
                SyncOperation(
                    gameId = item.gameId,
                    gameName = game.title,
                    coverPath = game.coverPath,
                    direction = SyncDirection.UPLOAD,
                    status = SyncStatus.PENDING
                )
            )
        }

        var processed = 0

        for (item in pending) {
            Logger.debug(TAG, "Processing pending upload: gameId=${item.gameId}, emulator=${item.emulatorId}")
            updateOperation(item.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = uploadSave(item.gameId, item.emulatorId)) {
                is SaveSyncResult.Success -> {
                    pendingSaveSyncDao.delete(item.id)
                    completeOperation(item.gameId)
                    processed++
                }
                is SaveSyncResult.Conflict -> {
                    Logger.debug(TAG, "Pending upload conflict for gameId=${item.gameId}, leaving in queue")
                    completeOperation(item.gameId, "Server has newer save")
                }
                is SaveSyncResult.Error -> {
                    Logger.debug(TAG, "Pending upload failed for gameId=${item.gameId}: ${result.message}")
                    pendingSaveSyncDao.incrementRetry(item.id, result.message)
                    completeOperation(item.gameId, result.message)
                }
                else -> {
                    completeOperation(item.gameId, "Sync not available")
                }
            }
        }

        Logger.info(TAG, "Processed $processed/${pending.size} pending uploads")
        processed
    }

    suspend fun downloadPendingServerSaves(): Int = withContext(Dispatchers.IO) {
        val pendingDownloads = saveSyncDao.getPendingDownloads()
        if (pendingDownloads.isEmpty()) {
            return@withContext 0
        }

        for (syncEntity in pendingDownloads) {
            val game = gameDao.getById(syncEntity.gameId) ?: continue
            addOperation(
                SyncOperation(
                    gameId = syncEntity.gameId,
                    gameName = game.title,
                    coverPath = game.coverPath,
                    direction = SyncDirection.DOWNLOAD,
                    status = SyncStatus.PENDING
                )
            )
        }

        var downloaded = 0

        for (syncEntity in pendingDownloads) {
            updateOperation(syncEntity.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = downloadSave(syncEntity.gameId, syncEntity.emulatorId, syncEntity.channelName)) {
                is SaveSyncResult.Success -> {
                    completeOperation(syncEntity.gameId)
                    downloaded++
                }
                is SaveSyncResult.Error -> {
                    completeOperation(syncEntity.gameId, result.message)
                }
                else -> {
                    completeOperation(syncEntity.gameId, "Download failed")
                }
            }
        }

        downloaded
    }

    suspend fun updateSyncEntity(
        gameId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?
    ) {
        val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        if (existing != null) {
            saveSyncDao.upsert(
                existing.copy(
                    localSavePath = localPath ?: existing.localSavePath,
                    localUpdatedAt = localUpdatedAt ?: existing.localUpdatedAt
                )
            )
        }
    }

    suspend fun createOrUpdateSyncEntity(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?,
        channelName: String? = null
    ): SaveSyncEntity {
        val existing = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        }
        val entity = SaveSyncEntity(
            id = existing?.id ?: 0,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = channelName,
            rommSaveId = existing?.rommSaveId,
            localSavePath = localPath ?: existing?.localSavePath,
            localUpdatedAt = localUpdatedAt ?: existing?.localUpdatedAt,
            serverUpdatedAt = existing?.serverUpdatedAt,
            lastSyncedAt = existing?.lastSyncedAt,
            syncStatus = existing?.syncStatus ?: SaveSyncEntity.STATUS_PENDING_UPLOAD
        )
        saveSyncDao.upsert(entity)
        return entity
    }

    suspend fun preLaunchSync(gameId: Long, rommId: Long, emulatorId: String): PreLaunchSyncResult =
        withContext(Dispatchers.IO) {
            Logger.debug(TAG, "preLaunchSync: gameId=$gameId, rommId=$rommId, emulator=$emulatorId")
            val api = this@SaveSyncRepository.api ?: return@withContext PreLaunchSyncResult.NoConnection

            try {
                val serverSaves = checkSavesForGame(gameId, rommId)
                Logger.debug(TAG, "preLaunchSync: found ${serverSaves.size} server saves")
                val serverSave = serverSaves.find { it.emulator == emulatorId || it.emulator == null }
                    ?: return@withContext PreLaunchSyncResult.NoServerSave

                val serverTime = parseTimestamp(serverSave.updatedAt)
                val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)

                val localFile = existing?.localSavePath?.let { File(it) }?.takeIf { it.exists() }
                val localFileTime = localFile?.let { Instant.ofEpochMilli(it.lastModified()) }

                Logger.debug(TAG, "preLaunchSync: serverTime=$serverTime (raw=${serverSave.updatedAt}), localFileTime=$localFileTime")

                if (localFileTime != null && !serverTime.isAfter(localFileTime)) {
                    Logger.debug(TAG, "preLaunchSync: local file is newer or equal")
                    return@withContext PreLaunchSyncResult.LocalIsNewer
                }

                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = existing?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        rommSaveId = serverSave.id,
                        localSavePath = existing?.localSavePath,
                        localUpdatedAt = existing?.localUpdatedAt,
                        serverUpdatedAt = serverTime,
                        lastSyncedAt = existing?.lastSyncedAt,
                        syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
                    )
                )

                PreLaunchSyncResult.ServerIsNewer(serverTime)
            } catch (e: Exception) {
                PreLaunchSyncResult.NoConnection
            }
        }

    sealed class PreLaunchSyncResult {
        data object NoConnection : PreLaunchSyncResult()
        data object NoServerSave : PreLaunchSyncResult()
        data object LocalIsNewer : PreLaunchSyncResult()
        data class ServerIsNewer(val serverTimestamp: Instant) : PreLaunchSyncResult()
    }

    @Suppress("SwallowedException")
    private fun parseTimestamp(timestamp: String): Instant {
        return try {
            Instant.parse(timestamp)
        } catch (e: Exception) {
            try {
                java.time.OffsetDateTime.parse(timestamp).toInstant()
            } catch (e2: Exception) {
                try {
                    java.time.ZonedDateTime.parse(timestamp).toInstant()
                } catch (e3: Exception) {
                    Logger.warn(TAG, "Failed to parse timestamp: $timestamp, using current time")
                    Instant.now()
                }
            }
        }
    }

    suspend fun syncSavesForNewDownload(gameId: Long, rommId: Long, emulatorId: String) = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return@withContext

        val serverSaves = checkSavesForGame(gameId, rommId)
        if (serverSaves.isEmpty()) return@withContext

        val game = gameDao.getById(gameId) ?: return@withContext
        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }

        for (serverSave in serverSaves) {
            val channelName = parseServerChannelNameForSync(serverSave.fileName, romBaseName)
            val serverTime = parseTimestamp(serverSave.updatedAt)

            saveSyncDao.upsert(
                SaveSyncEntity(
                    id = 0,
                    gameId = gameId,
                    rommId = rommId,
                    emulatorId = emulatorId,
                    channelName = channelName,
                    rommSaveId = serverSave.id,
                    localSavePath = null,
                    localUpdatedAt = null,
                    serverUpdatedAt = serverTime,
                    lastSyncedAt = null,
                    syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
                )
            )

            val result = downloadSave(gameId, emulatorId, channelName, skipBackup = true)
            if (result is SaveSyncResult.Error) {
                Logger.error(TAG, "syncSavesForNewDownload: failed '${serverSave.fileName}': ${result.message}")
            }
        }
    }

    private fun parseServerChannelNameForSync(fileName: String, romBaseName: String?): String? {
        val baseName = File(fileName).nameWithoutExtension
        if (isTimestampSaveName(baseName)) return null
        if (isLatestSaveFileName(fileName, romBaseName)) return null
        return baseName
    }

    private fun isLatestSaveFileName(fileName: String, romBaseName: String?): Boolean {
        val baseName = File(fileName).nameWithoutExtension
        if (baseName.equals(DEFAULT_SAVE_NAME, ignoreCase = true)) return true
        if (romBaseName == null) return false
        if (baseName.equals(romBaseName, ignoreCase = true)) return true
        if (baseName.startsWith(romBaseName, ignoreCase = true)) {
            val suffix = baseName.drop(romBaseName.length).trim()
            if (suffix.isEmpty()) return true
            if (ROMM_TIMESTAMP_TAG.matches(suffix)) return true
        }
        return false
    }

    companion object {
        private val ROMM_TIMESTAMP_TAG = Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2}(-\d+)?\]$""")
        private val SWITCH_EMULATOR_IDS = setOf(
            "yuzu", "ryujinx", "citron", "strato", "eden", "sudachi", "skyline"
        )
    }
}
