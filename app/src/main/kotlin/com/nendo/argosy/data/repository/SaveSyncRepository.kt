package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.emulator.EmulatorRegistry
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
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

    fun setApi(api: RomMApi?) {
        this.api = api
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
        platformId: String,
        romPath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        if (userConfig?.isUserOverride == true) {
            return@withContext findSaveInPath(userConfig.savePathPattern, gameTitle)
        }

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId) ?: return@withContext null

        if (config.usesFolderBasedSaves && romPath != null) {
            if (!isFolderSaveSyncEnabled()) {
                return@withContext null
            }
            return@withContext discoverFolderSavePath(config, platformId, romPath)
        }

        val paths = if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            val packageName = if (emulatorId == "retroarch_64") "com.retroarch.aarch64" else "com.retroarch"
            val coreName = SavePathRegistry.getRetroArchCore(platformId)
            val contentDir = romPath?.let { File(it).parent }
            retroArchConfigParser.resolveSavePaths(packageName, platformId, coreName, contentDir)
        } else {
            SavePathRegistry.resolvePath(config, platformId)
        }

        for (basePath in paths) {
            val saveFile = findSaveInPath(basePath, gameTitle, config.saveExtensions)
            if (saveFile != null) {
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

        null
    }

    private fun discoverFolderSavePath(
        config: SavePathConfig,
        platformId: String,
        romPath: String
    ): String? {
        val romFile = File(romPath)
        val titleId = titleIdExtractor.extractTitleId(romFile, platformId) ?: return null

        for (basePath in config.defaultPaths) {
            val saveFolder = findSaveFolderByTitleId(basePath, titleId, platformId)
            if (saveFolder != null) return saveFolder
        }
        return null
    }

    private fun findSaveFolderByTitleId(
        basePath: String,
        titleId: String,
        platformId: String
    ): String? {
        val baseDir = File(basePath)
        if (!baseDir.exists()) return null

        when (platformId) {
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

    fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformId: String,
        romPath: String?
    ): String? {
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null

        if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            return constructRetroArchSavePath(emulatorId, gameTitle, platformId, romPath)
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, platformId)
        val baseDir = resolvedPaths.firstOrNull { File(it).exists() }
            ?: resolvedPaths.firstOrNull()
            ?: return null

        val extension = config.saveExtensions.firstOrNull { it != "*" } ?: "sav"
        val sanitizedName = sanitizeFileName(gameTitle)
        val fileName = "$sanitizedName.$extension"

        return "$baseDir/$fileName"
    }

    private fun constructRetroArchSavePath(
        emulatorId: String,
        gameTitle: String,
        platformId: String,
        romPath: String?
    ): String? {
        val packageName = when (emulatorId) {
            "retroarch_64" -> "com.retroarch.aarch64"
            else -> "com.retroarch"
        }

        val raConfig = retroArchConfigParser.parse(packageName)
        val coreName = SavePathRegistry.getRetroArchCore(platformId) ?: return null
        val saveConfig = SavePathRegistry.getConfig(emulatorId) ?: return null
        val extension = saveConfig.saveExtensions.firstOrNull() ?: "srm"

        val baseDir = when {
            raConfig?.savefilesInContentDir == true && romPath != null -> {
                File(romPath).parent
            }
            raConfig?.savefileDirectory != null -> {
                if (raConfig.sortSavefilesByContentEnable) {
                    "${raConfig.savefileDirectory}/$coreName"
                } else {
                    raConfig.savefileDirectory
                }
            }
            else -> {
                val defaultPaths = SavePathRegistry.resolvePath(saveConfig, platformId)
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
            return "$romName.$extension"
        }

        val sanitized = gameTitle
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return "$sanitized.$extension"
    }

    fun constructSavePathWithFileName(
        emulatorId: String,
        platformId: String,
        romPath: String?,
        serverFileName: String
    ): String? {
        val baseDir = getSaveDirectory(emulatorId, platformId, romPath) ?: return null
        return "$baseDir/$serverFileName"
    }

    private fun getSaveDirectory(
        emulatorId: String,
        platformId: String,
        romPath: String?
    ): String? {
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null

        if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            return getRetroArchSaveDirectory(emulatorId, platformId, romPath)
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, platformId)
        return resolvedPaths.firstOrNull { File(it).exists() }
            ?: resolvedPaths.firstOrNull()
    }

    private fun getRetroArchSaveDirectory(
        emulatorId: String,
        platformId: String,
        romPath: String?
    ): String? {
        val packageName = when (emulatorId) {
            "retroarch_64" -> "com.retroarch.aarch64"
            else -> "com.retroarch"
        }

        val raConfig = retroArchConfigParser.parse(packageName)
        val coreName = SavePathRegistry.getRetroArchCore(platformId)
        val saveConfig = SavePathRegistry.getConfig(emulatorId) ?: return null

        return when {
            raConfig?.savefilesInContentDir == true && romPath != null -> {
                File(romPath).parent
            }
            raConfig?.savefileDirectory != null -> {
                if (raConfig.sortSavefilesByContentEnable && coreName != null) {
                    "${raConfig.savefileDirectory}/$coreName"
                } else {
                    raConfig.savefileDirectory
                }
            }
            else -> {
                val defaultPaths = SavePathRegistry.resolvePath(saveConfig, platformId)
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
        val platformIds = downloadedGames.mapNotNull { it.platformId.toLongOrNull() }.distinct()

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
        val api = this@SaveSyncRepository.api ?: return@withContext SaveSyncResult.NotConfigured

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        }

        val game = gameDao.getById(gameId) ?: return@withContext SaveSyncResult.Error("Game not found")
        val rommId = game.rommId ?: return@withContext SaveSyncResult.NotConfigured

        val localPath = syncEntity?.localSavePath
            ?: discoverSavePath(emulatorId, game.title, game.platformId, game.localPath)
            ?: return@withContext SaveSyncResult.NoSaveFound

        val saveLocation = File(localPath)
        if (!saveLocation.exists()) return@withContext SaveSyncResult.NoSaveFound

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isFolderBased = config?.usesFolderBasedSaves == true && saveLocation.isDirectory

        if (isFolderBased && !isFolderSaveSyncEnabled()) {
            return@withContext SaveSyncResult.NotConfigured
        }

        val localModified = Instant.ofEpochMilli(saveLocation.lastModified())

        if (channelName == null &&
            syncEntity?.serverUpdatedAt != null &&
            syncEntity.serverUpdatedAt.isAfter(syncEntity.lastSyncedAt ?: Instant.EPOCH) &&
            syncEntity.serverUpdatedAt.isAfter(localModified)
        ) {
            return@withContext SaveSyncResult.Conflict(gameId, localModified, syncEntity.serverUpdatedAt)
        }

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

            val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
            val serverSaves = checkSavesForGame(gameId, rommId)
            val existingServerSave = serverSaves.find { serverSave ->
                val baseName = File(serverSave.fileName).nameWithoutExtension
                if (channelName != null) {
                    baseName.equals(channelName, ignoreCase = true)
                } else {
                    baseName.equals(DEFAULT_SAVE_NAME, ignoreCase = true) ||
                        romBaseName != null && baseName.equals(romBaseName, ignoreCase = true)
                }
            }

            val serverSaveIdToUpdate = syncEntity?.rommSaveId ?: existingServerSave?.id

            val requestBody = fileToUpload.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, requestBody)

            val response = if (serverSaveIdToUpdate != null) {
                api.updateSave(serverSaveIdToUpdate, filePart)
            } else {
                api.uploadSave(rommId, emulatorId, filePart)
            }

            if (response.isSuccessful) {
                val serverSave = response.body()!!
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
                Log.e(TAG, "uploadSave failed: ${response.code()}")
                SaveSyncResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadSave exception", e)
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
            Log.e(TAG, "downloadSave: getSave failed", e)
            return@withContext SaveSyncResult.Error("Failed to get save info: ${e.message}")
        } ?: return@withContext SaveSyncResult.Error("Save not found on server")

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true)

        if (isFolderBased && !isFolderSaveSyncEnabled()) {
            return@withContext SaveSyncResult.NotConfigured
        }

        val targetPath = if (isFolderBased) {
            syncEntity.localSavePath
                ?: constructFolderSavePath(emulatorId, game.platformId, game.localPath)
        } else {
            syncEntity.localSavePath
                ?: discoverSavePath(emulatorId, game.title, game.platformId, game.localPath)
                ?: constructSavePathWithFileName(emulatorId, platformId = game.platformId, romPath = game.localPath, serverFileName = serverSave.fileName)
        } ?: return@withContext SaveSyncResult.Error("Cannot determine save path")

        val targetFile = File(targetPath)
        if (targetFile.exists() && !skipBackup) {
            try {
                saveCacheManager.get().cacheCurrentSave(gameId, emulatorId, targetPath)
                Log.d(TAG, "Cached existing save before download for game $gameId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache existing save before download", e)
            }
        }

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath
                ?: return@withContext SaveSyncResult.Error("No download path available")

            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Log.e(TAG, "downloadSave failed: ${response.code()}")
                return@withContext SaveSyncResult.Error("Download failed: ${response.code()}")
            }

            if (isFolderBased) {
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                response.body()?.byteStream()?.use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val targetFolder = File(targetPath)
                targetFolder.mkdirs()

                if (!saveArchiver.unzipSingleFolder(tempZipFile, targetFolder)) {
                    return@withContext SaveSyncResult.Error("Failed to unzip save")
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
                Log.e(TAG, "Cache creation failed", e)
            }

            SaveSyncResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "downloadSave exception", e)
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
            Log.e(TAG, "downloadSaveById: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true)

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath ?: return@withContext false

            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Log.e(TAG, "downloadSaveById failed: ${response.code()}")
                return@withContext false
            }

            if (isFolderBased) {
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                response.body()?.byteStream()?.use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val targetFolder = File(targetPath)
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
            Log.e(TAG, "downloadSaveById exception", e)
            false
        } finally {
            tempZipFile?.delete()
        }
    }

    private fun constructFolderSavePath(
        emulatorId: String,
        platformId: String,
        romPath: String?
    ): String? {
        if (romPath == null) return null

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId) ?: return null
        if (!config.usesFolderBasedSaves) return null

        val romFile = File(romPath)
        val titleId = titleIdExtractor.extractTitleId(romFile, platformId) ?: return null

        val baseDir = config.defaultPaths.firstOrNull { File(it).exists() }
            ?: config.defaultPaths.firstOrNull()
            ?: return null

        return when (platformId) {
            "vita", "psvita" -> "$baseDir/$titleId"
            "switch" -> {
                val existingUserFolder = File(baseDir).listFiles()?.firstOrNull { it.isDirectory }
                if (existingUserFolder != null) {
                    "${existingUserFolder.absolutePath}/$titleId"
                } else {
                    "$baseDir/0000000000000001/$titleId"
                }
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
        var processed = 0

        for (item in pending) {
            when (val result = uploadSave(item.gameId, item.emulatorId)) {
                is SaveSyncResult.Success -> {
                    pendingSaveSyncDao.delete(item.id)
                    processed++
                }
                is SaveSyncResult.Conflict -> {
                    // Leave in queue for user resolution
                }
                is SaveSyncResult.Error -> {
                    pendingSaveSyncDao.incrementRetry(item.id, result.message)
                }
                else -> {}
            }
        }

        processed
    }

    suspend fun downloadPendingServerSaves(): Int = withContext(Dispatchers.IO) {
        val pendingDownloads = saveSyncDao.getPendingDownloads()
        var downloaded = 0

        for (syncEntity in pendingDownloads) {
            when (downloadSave(syncEntity.gameId, syncEntity.emulatorId, syncEntity.channelName)) {
                is SaveSyncResult.Success -> downloaded++
                else -> {}
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
            val api = this@SaveSyncRepository.api ?: return@withContext PreLaunchSyncResult.NoConnection

            try {
                val serverSaves = checkSavesForGame(gameId, rommId)
                val serverSave = serverSaves.find { it.emulator == emulatorId || it.emulator == null }
                    ?: return@withContext PreLaunchSyncResult.NoServerSave

                val serverTime = parseTimestamp(serverSave.updatedAt)
                val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)

                val localFileExists = existing?.localSavePath?.let { File(it).exists() } ?: false

                if (localFileExists && existing?.localUpdatedAt != null && !serverTime.isAfter(existing.localUpdatedAt)) {
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

    private fun parseTimestamp(timestamp: String): Instant {
        return try {
            Instant.parse(timestamp)
        } catch (e: Exception) {
            try {
                DateTimeFormatter.ISO_DATE_TIME.parse(timestamp, Instant::from)
            } catch (e2: Exception) {
                Instant.now()
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
                Log.e(TAG, "syncSavesForNewDownload: failed '${serverSave.fileName}': ${result.message}")
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
        return baseName.equals(DEFAULT_SAVE_NAME, ignoreCase = true) ||
            romBaseName != null && baseName.equals(romBaseName, ignoreCase = true)
    }
}
