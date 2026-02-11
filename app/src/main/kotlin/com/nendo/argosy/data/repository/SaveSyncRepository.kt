package com.nendo.argosy.data.repository

import android.content.Context
import android.os.StatFs
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.GameCubeHeaderParser
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.TitleIdExtractor
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.SyncOperation
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncQueueState
import com.nendo.argosy.data.sync.SyncStatus
import com.nendo.argosy.data.sync.platform.DefaultSaveHandler
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.N3dsSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandler
import com.nendo.argosy.data.sync.platform.PspSaveHandler
import com.nendo.argosy.data.sync.platform.RetroArchSaveHandler
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.sync.platform.VitaSaveHandler
import com.nendo.argosy.data.sync.platform.WiiUSaveHandler
import com.nendo.argosy.data.emulator.TitleIdResult
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.sync.SaveFilePayload
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import java.io.File
import java.time.Instant
import org.apache.commons.compress.archivers.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SaveSyncRepository"
private const val DEFAULT_SAVE_NAME = "argosy-latest"
private const val MIN_VALID_SAVE_SIZE_BYTES = 100L
private val TIMESTAMP_ONLY_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}[_-]\d{2}[_-]\d{2}[_-]\d{2}$""")


sealed class SaveSyncResult {
    data object Success : SaveSyncResult()
    data class Conflict(
        val gameId: Long,
        val localTimestamp: Instant,
        val serverTimestamp: Instant
    ) : SaveSyncResult()
    data class NeedsHardcoreResolution(
        val tempFilePath: String,
        val gameId: Long,
        val gameName: String,
        val emulatorId: String,
        val targetPath: String,
        val isFolderBased: Boolean,
        val channelName: String?
    ) : SaveSyncResult()
    data class Error(val message: String) : SaveSyncResult()
    data object NoSaveFound : SaveSyncResult()
    data object NotConfigured : SaveSyncResult()
}

@Singleton
class SaveSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val saveCacheDao: SaveCacheDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val retroArchConfigParser: RetroArchConfigParser,
    private val titleIdExtractor: TitleIdExtractor,
    private val titleDbRepository: TitleDbRepository,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val fal: FileAccessLayer,
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val n3dsSaveHandler: N3dsSaveHandler,
    private val vitaSaveHandler: VitaSaveHandler,
    private val pspSaveHandler: PspSaveHandler,
    private val wiiUSaveHandler: WiiUSaveHandler,
    private val retroArchSaveHandler: RetroArchSaveHandler,
    private val defaultSaveHandler: DefaultSaveHandler,
    private val syncQueueManager: SyncQueueManager
) {
    private var api: RomMApi? = null

    val syncQueueState: StateFlow<SyncQueueState> = syncQueueManager.state

    fun setApi(api: RomMApi?) {
        this.api = api
    }

    fun getApi(): RomMApi? = api

    fun clearCompletedOperations() = syncQueueManager.clearCompletedOperations()

    private fun resolveSavePaths(config: SavePathConfig, platformSlug: String): List<String> {
        val filesDir = if (config.usesInternalStorage) context.filesDir.absolutePath else null
        return SavePathRegistry.resolvePath(config, platformSlug, filesDir)
    }

    private fun listFilesAtPath(path: String): List<FileInfo>? = fal.listFiles(path)

    private fun directoryExists(path: String): Boolean = fal.exists(path) && fal.isDirectory(path)

    private suspend fun isFolderSaveSyncEnabled(): Boolean {
        val prefs = userPreferencesRepository.preferences.first()
        return prefs.saveSyncEnabled && prefs.experimentalFolderSaveSync
    }

    private fun getHandler(config: SavePathConfig?, platformSlug: String, emulatorId: String): PlatformSaveHandler {
        return when {
            emulatorId in listOf("retroarch", "retroarch_64") -> retroArchSaveHandler
            config?.usesGciFormat == true -> gciSaveHandler
            platformSlug == "switch" -> switchSaveHandler
            platformSlug == "3ds" -> n3dsSaveHandler
            platformSlug in listOf("vita", "psvita") -> vitaSaveHandler
            platformSlug == "psp" -> pspSaveHandler
            platformSlug == "wiiu" -> wiiUSaveHandler
            else -> defaultSaveHandler
        }
    }

    fun observeNewSavesCount(): Flow<Int> = saveSyncDao.observeNewSavesCount()

    fun observePendingCount(): Flow<Int> = saveCacheDao.observeNeedingRemoteSyncCount()

    suspend fun discoverSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String? = null,
        cachedTitleId: String? = null,
        coreName: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null
    ): String? = savePathResolver.discoverSavePath(
        emulatorId = emulatorId,
        gameTitle = gameTitle,
        platformSlug = platformSlug,
        romPath = romPath,
        cachedTitleId = cachedTitleId,
        coreName = coreName,
        emulatorPackage = emulatorPackage,
        gameId = gameId,
        isFolderSaveSyncEnabled = isFolderSaveSyncEnabled()
    )

    private suspend fun extractGciBundle(
        zipFile: File,
        config: SavePathConfig,
        romPath: String,
        gameId: Long
    ): List<String> = gciSaveHandler.extractBundle(zipFile, config, romPath, gameId)

    private fun isValidSwitchHexId(name: String): Boolean = switchSaveHandler.isValidHexId(name)

    private fun isValidSwitchTitleId(titleId: String): Boolean = switchSaveHandler.isValidTitleId(titleId)

    suspend fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?
    ): String? = savePathResolver.constructSavePath(emulatorId, gameTitle, platformSlug, romPath)

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
            Logger.error(TAG, "[SaveSync] WORKER | getSavesByPlatform failed | platformId=$platformId", e)
            return@withContext emptyList()
        }

        if (!response.isSuccessful) {
            Logger.warn(TAG, "[SaveSync] WORKER | getSavesByPlatform HTTP error | platformId=$platformId, status=${response.code()}")
            return@withContext emptyList()
        }

        val serverSaves = response.body() ?: return@withContext emptyList()
        val updatedEntities = mutableListOf<SaveSyncEntity>()

        val downloadedGames = gameDao.getGamesWithLocalPath()
            .filter { it.rommId != null }

        for (serverSave in serverSaves) {
            val game = downloadedGames.find { it.rommId == serverSave.romId } ?: continue
            val emulatorId = serverSave.emulator?.takeIf { it != "default" && it.isNotBlank() }
                ?: resolveEmulatorForGame(game)
            if (emulatorId == null) {
                Logger.warn(TAG, "[SaveSync] WORKER gameId=${game.id} | Skipping save - cannot resolve emulator | serverSaveId=${serverSave.id}, fileName=${serverSave.fileName}")
                continue
            }
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
            Logger.error(TAG, "[SaveSync] UPLOAD | getSavesByRom failed | gameId=$gameId, rommId=$rommId", e)
            return@withContext emptyList()
        }

        if (!response.isSuccessful) {
            Logger.warn(TAG, "[SaveSync] UPLOAD | getSavesByRom HTTP error | gameId=$gameId, rommId=$rommId, status=${response.code()}")
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

    private suspend fun resolveEmulatorForGame(game: GameEntity): String? {
        val gameConfig = emulatorConfigDao.getByGameId(game.id)
        if (gameConfig?.packageName != null) {
            val resolved = emulatorResolver.resolveEmulatorId(gameConfig.packageName)
            if (resolved != null) {
                Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Resolved emulator from game config | package=${gameConfig.packageName}, emulatorId=$resolved")
                return resolved
            }
        }

        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformConfig?.packageName != null) {
            val resolved = emulatorResolver.resolveEmulatorId(platformConfig.packageName)
            if (resolved != null) {
                Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Resolved emulator from platform default | package=${platformConfig.packageName}, emulatorId=$resolved")
                return resolved
            }
        }

        val installedEmulators = emulatorResolver.getInstalledForPlatform(game.platformSlug)
        if (installedEmulators.isNotEmpty()) {
            val emulatorId = installedEmulators.first().def.id
            Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Using first installed emulator for platform=${game.platformSlug} | emulatorId=$emulatorId, installed=${installedEmulators.map { it.def.id }}")
            return emulatorId
        }

        Logger.warn(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Cannot resolve emulator | platform=${game.platformSlug}, no config and no installed emulators")
        return null
    }

    suspend fun uploadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        forceOverwrite: Boolean = false,
        isHardcore: Boolean = false
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId emulator=$emulatorId channel=$channelName | Starting upload")
        val api = this@SaveSyncRepository.api
        if (api == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | No API connection")
            return@withContext SaveSyncResult.NotConfigured
        }

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, DEFAULT_SAVE_NAME)
        }

        val game = gameDao.getById(gameId)
        if (game == null) {
            Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Game not found in database")
            return@withContext SaveSyncResult.Error("Game not found")
        }
        val rommId = game.rommId
        if (rommId == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | No rommId, game not synced with RomM")
            return@withContext SaveSyncResult.NotConfigured
        }

        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Cannot resolve emulator from config")
                return@withContext SaveSyncResult.Error("Cannot determine emulator")
            }
        } else {
            emulatorId
        }
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Using emulator=$resolvedEmulatorId (original=$emulatorId)")

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)

        val folderSyncEnabled = isFolderSaveSyncEnabled()
        val cachedPath = syncEntity?.localSavePath?.takeIf { path ->
            if (game.platformSlug == "switch") switchSaveHandler.isValidCachedSavePath(path) else true
        }
        if (syncEntity?.localSavePath != null && cachedPath == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Rejecting invalid cached Switch path=${syncEntity.localSavePath}")
        }
        var localPath = cachedPath
            ?: savePathResolver.discoverSavePath(
                emulatorId = resolvedEmulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                emulatorPackage = emulatorPackage,
                gameId = gameId,
                isFolderSaveSyncEnabled = folderSyncEnabled
            )

        // If no path found and we had cached titleId data, clear it and retry with fresh lookup
        if (localPath == null && (game.titleId != null || titleDbRepository.getCachedCandidates(gameId).isNotEmpty())) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Clearing stale titleId cache and retrying")
            titleDbRepository.clearTitleIdCache(gameId)
            localPath = savePathResolver.discoverSavePath(
                emulatorId = resolvedEmulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = null,
                emulatorPackage = emulatorPackage,
                gameId = gameId,
                isFolderSaveSyncEnabled = folderSyncEnabled
            )
        }

        if (localPath == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Could not discover save path | emulator=$emulatorId, platform=${game.platformSlug}")
            return@withContext SaveSyncResult.NoSaveFound
        }

        if (!fal.exists(localPath)) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Save file does not exist | path=$localPath")
            return@withContext SaveSyncResult.NoSaveFound
        }

        val config = SavePathRegistry.getConfigIncludingUnsupported(resolvedEmulatorId)
        val isDirectory = fal.isDirectory(localPath)
        val isFolderBased = config?.usesFolderBasedSaves == true && isDirectory
        val isGciBundle = config?.usesGciFormat == true

        if (isFolderBased && !isFolderSaveSyncEnabled()) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Folder save sync disabled, skipping")
            return@withContext SaveSyncResult.NotConfigured
        }

        val localModified = if (isDirectory) {
            Instant.ofEpochMilli(savePathResolver.findNewestFileTime(localPath))
        } else {
            Instant.ofEpochMilli(fal.lastModified(localPath))
        }
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Local modified time | localModified=$localModified")

        val handler = getHandler(config, game.platformSlug, resolvedEmulatorId)
        val saveContext = SaveContext(
            config = config ?: SavePathConfig(
                emulatorId = resolvedEmulatorId,
                defaultPaths = emptyList(),
                saveExtensions = listOf("sav", "srm")
            ),
            romPath = game.localPath,
            titleId = game.titleId,
            emulatorPackage = emulatorPackage,
            gameId = gameId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            emulatorId = resolvedEmulatorId,
            localSavePath = localPath
        )

        val prepared = handler.prepareForUpload(localPath, saveContext)
        if (prepared == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Handler returned no prepared save")
            return@withContext SaveSyncResult.NoSaveFound
        }

        val fileToUpload = prepared.file
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Save prepared | file=${fileToUpload.absolutePath}, size=${fileToUpload.length()}bytes, isTemporary=${prepared.isTemporary}")

        if (fileToUpload.length() <= MIN_VALID_SAVE_SIZE_BYTES) {
            Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Rejecting empty save | size=${fileToUpload.length()}bytes, minRequired=$MIN_VALID_SAVE_SIZE_BYTES")
            if (prepared.isTemporary) fileToUpload.delete()
            return@withContext SaveSyncResult.NoSaveFound
        }

        var tempTrailerFile: File? = null

        try {
            val uploadFile = if (isHardcore) {
                if (prepared.isTemporary) {
                    saveArchiver.appendHardcoreTrailer(fileToUpload)
                    Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Appended hardcore trailer to prepared file")
                    fileToUpload
                } else {
                    tempTrailerFile = File(context.cacheDir, "upload_${fileToUpload.name}")
                    fileToUpload.copyTo(tempTrailerFile, overwrite = true)
                    saveArchiver.appendHardcoreTrailer(tempTrailerFile)
                    Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Appended hardcore trailer to copy")
                    tempTrailerFile
                }
            } else {
                fileToUpload
            }

            val contentHash = saveArchiver.calculateFileHash(uploadFile)
            if (syncEntity?.lastUploadedHash == contentHash) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Skipped - content unchanged (hash=$contentHash)")
                if (prepared.isTemporary) fileToUpload.delete()
                tempTrailerFile?.delete()
                return@withContext SaveSyncResult.Success
            }

            val romFile = game.localPath?.let { File(it) }
            val romBaseName = romFile?.nameWithoutExtension

            val uploadFileName = if (channelName != null) {
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$channelName.$ext" else channelName
            } else {
                val baseName = romBaseName ?: DEFAULT_SAVE_NAME
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$baseName.$ext" else baseName
            }

            val serverSaves = checkSavesForGame(gameId, rommId)
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Server saves found | count=${serverSaves.size}, files=${serverSaves.map { it.fileName }}")

            // For GCI format, prefer ZIP bundles over single .gci files
            val latestServerSave = if (channelName != null) {
                serverSaves.find { File(it.fileName).nameWithoutExtension.equals(channelName, ignoreCase = true) }
            } else {
                val candidates = serverSaves.filter { isLatestSaveFileName(it.fileName, romBaseName) }
                if (isGciBundle && candidates.size > 1) {
                    candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                        ?: candidates.firstOrNull()
                } else {
                    candidates.firstOrNull()
                }
            }

            val existingServerSave = run {
                val candidates = serverSaves.filter { serverSave ->
                    val baseName = File(serverSave.fileName).nameWithoutExtension
                    if (channelName != null) {
                        baseName.equals(channelName, ignoreCase = true)
                    } else {
                        baseName.equals(DEFAULT_SAVE_NAME, ignoreCase = true) ||
                            romBaseName != null && baseName.equals(romBaseName, ignoreCase = true)
                    }
                }
                // For GCI bundles, prefer ZIP over single .gci
                if (isGciBundle && candidates.size > 1) {
                    candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                        ?: candidates.firstOrNull()
                } else {
                    candidates.firstOrNull()
                }
            }

            if (!forceOverwrite && channelName == null && latestServerSave != null) {
                val serverTime = parseTimestamp(latestServerSave.updatedAt)
                val deltaMs = serverTime.toEpochMilli() - localModified.toEpochMilli()
                val deltaStr = if (deltaMs >= 0) "+${deltaMs/1000}s" else "${deltaMs/1000}s"
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Conflict check | local=$localModified, server=$serverTime, delta=$deltaStr")
                if (serverTime.isAfter(localModified)) {
                    Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=CONFLICT | Server is newer, blocking upload")
                    return@withContext SaveSyncResult.Conflict(gameId, localModified, serverTime)
                }
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=PROCEED | Local is newer or equal")
            } else if (forceOverwrite) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Skipping conflict check (force overwrite)")
            }

            // GCI bundle migration: if uploading a bundle but server has single .gci, delete old and create new
            val needsGciMigration = isGciBundle && existingServerSave != null &&
                !existingServerSave.fileName.endsWith(".gci.zip", ignoreCase = true) &&
                existingServerSave.fileName.endsWith(".gci", ignoreCase = true)

            if (needsGciMigration) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: deleting old single-file save | saveId=${existingServerSave!!.id}, fileName=${existingServerSave.fileName}")
                try {
                    val deleteResponse = api.deleteSave(existingServerSave.id)
                    if (deleteResponse.isSuccessful) {
                        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: old save deleted successfully")
                    } else {
                        Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: failed to delete old save | status=${deleteResponse.code()}")
                    }
                } catch (e: Exception) {
                    Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: failed to delete old save", e)
                }
            }

            val serverSaveIdToUpdate = if (needsGciMigration) {
                // Don't update, create new
                null
            } else if (serverSaves.isNotEmpty()) {
                existingServerSave?.id
            } else {
                syncEntity?.rommSaveId
            }
            val isUpdate = serverSaveIdToUpdate != null
            val uploadStartTime = System.currentTimeMillis()
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | HTTP request | isUpdate=$isUpdate, saveIdToUpdate=$serverSaveIdToUpdate, fileName=$uploadFileName, size=${uploadFile.length()}bytes, serverSavesCount=${serverSaves.size}")

            SaveDebugLogger.logSyncUploadStarted(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                sizeBytes = uploadFile.length(),
                contentHash = contentHash
            )

            val requestBody = uploadFile.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, requestBody)

            var response = if (serverSaveIdToUpdate != null) {
                api.updateSave(serverSaveIdToUpdate, filePart)
            } else {
                api.uploadSave(rommId, resolvedEmulatorId, filePart)
            }

            if (!response.isSuccessful && serverSaveIdToUpdate != null) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Update failed, retrying as new upload | status=${response.code()}")
                val retryRequestBody = uploadFile.asRequestBody("application/octet-stream".toMediaType())
                val retryFilePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, retryRequestBody)
                response = api.uploadSave(rommId, resolvedEmulatorId, retryFilePart)
            }

            if (response.isSuccessful) {
                val serverSave = response.body()
                    ?: return@withContext SaveSyncResult.Error("Empty response from server")
                Logger.info(TAG, "[SaveSync] UPLOAD gameId=$gameId | Complete | serverSaveId=${serverSave.id}, fileName=$uploadFileName")
                val serverTimestamp = parseTimestamp(serverSave.updatedAt)
                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = syncEntity?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = resolvedEmulatorId,
                        channelName = channelName,
                        rommSaveId = serverSave.id,
                        localSavePath = localPath,
                        localUpdatedAt = serverTimestamp,
                        serverUpdatedAt = serverTimestamp,
                        lastSyncedAt = Instant.now(),
                        syncStatus = SaveSyncEntity.STATUS_SYNCED,
                        lastUploadedHash = contentHash
                    )
                )

                SaveDebugLogger.logSyncUploadCompleted(
                    gameId = gameId,
                    gameName = game.title,
                    channel = channelName,
                    serverId = serverSave.id,
                    durationMs = System.currentTimeMillis() - uploadStartTime
                )

                SaveSyncResult.Success
            } else {
                val errorBody = response.errorBody()?.string()
                Logger.error(TAG, "[SaveSync] UPLOAD gameId=$gameId | HTTP failed | status=${response.code()}, body=$errorBody")

                SaveDebugLogger.logSyncUploadFailed(
                    gameId = gameId,
                    gameName = game.title,
                    channel = channelName,
                    error = "HTTP ${response.code()}: $errorBody"
                )

                SaveSyncResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] UPLOAD gameId=$gameId | Exception during upload", e)

            SaveDebugLogger.logSyncUploadFailed(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                error = e.message ?: "Unknown exception"
            )

            SaveSyncResult.Error(e.message ?: "Upload failed")
        } finally {
            if (prepared.isTemporary) fileToUpload.delete()
            tempTrailerFile?.delete()
        }
    }

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId emulator=$emulatorId channel=$channelName | Starting download")
        val api = this@SaveSyncRepository.api
        if (api == null) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No API connection")
            return@withContext SaveSyncResult.NotConfigured
        }

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, DEFAULT_SAVE_NAME)
        }
        if (syncEntity == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No sync entity found in database")
            return@withContext SaveSyncResult.Error("No save tracking found")
        }

        val saveId = syncEntity.rommSaveId
        if (saveId == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No server save ID in sync entity")
            return@withContext SaveSyncResult.Error("No server save ID")
        }

        val game = gameDao.getById(gameId)
        if (game == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Game not found in database")
            return@withContext SaveSyncResult.Error("Game not found")
        }

        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot resolve emulator from config")
                return@withContext SaveSyncResult.Error("Cannot determine emulator")
            }
        } else {
            emulatorId
        }
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Using emulator=$resolvedEmulatorId (original=$emulatorId)")

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)

        val serverSave = try {
            api.getSave(saveId).body()
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | getSave API call failed", e)
            return@withContext SaveSyncResult.Error("Failed to get save info: ${e.message}")
        }
        if (serverSave == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Save not found on server | saveId=$saveId")
            return@withContext SaveSyncResult.Error("Save not found on server")
        }

        val config = SavePathRegistry.getConfigIncludingUnsupported(resolvedEmulatorId)
        val isGciFormat = config?.usesGciFormat == true
        // GCI bundles are detected by content (ZIP magic) after download, not by filename
        // This handles cases where server filename wasn't updated during migration
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true) && !isGciFormat
        val isSwitchEmulator = resolvedEmulatorId in SWITCH_EMULATOR_IDS
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Save info | fileName=${serverSave.fileName}, isFolderBased=$isFolderBased, isGciFormat=$isGciFormat, isSwitchEmulator=$isSwitchEmulator")

        val folderSyncEnabled = isFolderSaveSyncEnabled()
        if (isFolderBased && !folderSyncEnabled) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Folder save sync disabled, skipping")
            return@withContext SaveSyncResult.NotConfigured
        }

        val preDownloadTargetPath = if (isGciFormat) {
            // GCI format: download to temp first, then detect if bundle or single file
            null.also {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | GCI format, will download to temp and detect bundle vs single")
            }
        } else if (isFolderBased) {
            if (isSwitchEmulator) {
                null.also {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Switch emulator, will discover active profile")
                }
            } else {
                val cached = syncEntity.localSavePath
                val discovered = if (cached == null) {
                    savePathResolver.discoverSavePath(
                        emulatorId = resolvedEmulatorId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        romPath = game.localPath,
                        cachedTitleId = game.titleId,
                        emulatorPackage = emulatorPackage,
                        gameId = gameId,
                        isFolderSaveSyncEnabled = folderSyncEnabled
                    )
                } else null
                val constructed = if (cached == null && discovered == null) {
                    savePathResolver.constructFolderSavePathWithOverride(resolvedEmulatorId, game.platformSlug, game.localPath, gameId, game.title, game.titleId, emulatorPackage)
                } else null
                (cached ?: discovered ?: constructed).also {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Folder save path | cached=${cached != null}, discovered=${discovered != null}, constructed=${constructed != null}, path=$it")
                }
            }
        } else {
            val discovered = syncEntity.localSavePath
                ?: savePathResolver.discoverSavePath(
                    emulatorId = resolvedEmulatorId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    romPath = game.localPath,
                    cachedTitleId = game.titleId,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    isFolderSaveSyncEnabled = folderSyncEnabled
                )

            // If discovery failed and we had cached titleId data, clear it and retry
            val retried = if (discovered == null && (game.titleId != null || titleDbRepository.getCachedCandidates(gameId).isNotEmpty())) {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Clearing stale titleId cache and retrying")
                titleDbRepository.clearTitleIdCache(gameId)
                savePathResolver.discoverSavePath(
                    emulatorId = resolvedEmulatorId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    romPath = game.localPath,
                    cachedTitleId = null,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    isFolderSaveSyncEnabled = folderSyncEnabled
                )
            } else discovered

            (retried ?: savePathResolver.constructSavePath(resolvedEmulatorId, game.title, game.platformSlug, game.localPath)).also {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | File save path | cached=${syncEntity.localSavePath != null}, discovered=${retried != null}, path=$it")
            }
        }

        if (!isSwitchEmulator && !isFolderBased && !isGciFormat && preDownloadTargetPath == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine save path for non-Switch file save")
            return@withContext SaveSyncResult.Error("Cannot determine save path")
        }

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath
            if (downloadPath == null) {
                Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No download path in server save response")
                return@withContext SaveSyncResult.Error("No download path available")
            }

            val downloadStartTime = System.currentTimeMillis()
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP request starting | downloadPath=$downloadPath")

            SaveDebugLogger.logSyncDownloadStarted(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                serverId = saveId
            )

            val response = try {
                withRetry(tag = "[SaveSync] DOWNLOAD gameId=$gameId") {
                    api.downloadRaw(downloadPath)
                }
            } catch (e: IOException) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP failed after retries", e)
                return@withContext SaveSyncResult.Error("Download failed: ${e.message}")
            }
            val contentLength = response.headers()["Content-Length"]?.toLongOrNull() ?: -1
            if (!response.isSuccessful) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP failed | status=${response.code()}, message=${response.message()}")
                return@withContext SaveSyncResult.Error("Download failed: ${response.code()}")
            }
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP success | status=${response.code()}, size=${contentLength}bytes")

            var targetPath: String

            if (isFolderBased) {
                if (!hasEnoughDiskSpace(context.cacheDir.absolutePath, contentLength)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient cache disk space for zip")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Response body is null")
                    return@withContext SaveSyncResult.Error("Empty response body")
                }
                body.byteStream().use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Saved temp zip | path=${tempZipFile.absolutePath}, size=${tempZipFile.length()}bytes")

                targetPath = if (isSwitchEmulator && config != null) {
                    val resolved = preDownloadTargetPath
                        ?: savePathResolver.resolveSwitchSaveTargetPath(tempZipFile, config, emulatorPackage)
                        ?: savePathResolver.constructFolderSavePath(resolvedEmulatorId, game.platformSlug, game.localPath, emulatorPackage)
                    if (resolved == null) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine Switch save path from ZIP or ROM")
                        return@withContext SaveSyncResult.Error("Cannot determine save path from ZIP or ROM")
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Switch save target resolved | path=$resolved, method=${if (preDownloadTargetPath != null) "cached" else "from_zip_or_rom"}")
                    resolved
                } else {
                    preDownloadTargetPath ?: run {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine folder save path")
                        return@withContext SaveSyncResult.Error("Cannot determine save path")
                    }
                }

                val hasLocalHardcore = saveCacheManager.get().hasHardcoreSave(gameId)
                val downloadedHasTrailer = saveArchiver.hasHardcoreTrailer(tempZipFile)
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Hardcore check | localHardcore=$hasLocalHardcore, downloadedTrailer=$downloadedHasTrailer")
                if (hasLocalHardcore && !downloadedHasTrailer) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | NEEDS_HARDCORE_RESOLUTION | Server save missing trailer")
                    val tempPath = tempZipFile.absolutePath
                    tempZipFile = null
                    return@withContext SaveSyncResult.NeedsHardcoreResolution(
                        tempFilePath = tempPath,
                        gameId = gameId,
                        gameName = game.title,
                        emulatorId = resolvedEmulatorId,
                        targetPath = targetPath,
                        isFolderBased = true,
                        channelName = channelName ?: syncEntity.channelName
                    )
                }

                val existingTarget = File(targetPath)
                if (existingTarget.exists() && !skipBackup) {
                    try {
                        saveCacheManager.get().cacheCurrentSave(gameId, resolvedEmulatorId, targetPath)
                        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached existing save before overwrite")
                    } catch (e: Exception) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed, aborting download to prevent data loss", e)
                        return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                    }
                }

                val extractedSize = tempZipFile.length() * 3
                if (!hasEnoughDiskSpace(targetPath, extractedSize)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient disk space for extracted save")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                // Re-fetch title ID as it may have been resolved during path discovery
                val resolvedTitleId = game.titleId ?: gameDao.getTitleId(gameId)

                val saveContext = SaveContext(
                    config = config!!,
                    romPath = game.localPath,
                    titleId = resolvedTitleId,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    emulatorId = resolvedEmulatorId,
                    localSavePath = targetPath
                )
                val handler = getHandler(config, game.platformSlug, resolvedEmulatorId)
                val result = handler.extractDownload(tempZipFile, saveContext)
                if (!result.success) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Extraction failed | error=${result.error}")
                    return@withContext SaveSyncResult.Error(result.error ?: "Failed to extract save")
                }
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Extraction complete | target=${result.targetPath}")
            } else if (isGciFormat) {
                // GCI format: download to temp, then delegate extraction to handler
                if (!hasEnoughDiskSpace(context.cacheDir.absolutePath, contentLength)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient cache disk space for GCI save")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                if (game.localPath == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot extract GCI without ROM path")
                    return@withContext SaveSyncResult.Error("Cannot determine save path without ROM")
                }

                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Response body is null for GCI save")
                    return@withContext SaveSyncResult.Error("Empty response body")
                }

                val tempGciFile = File(context.cacheDir, "temp_gci_${System.currentTimeMillis()}.tmp")
                try {
                    body.byteStream().use { input ->
                        tempGciFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Saved GCI temp file | path=${tempGciFile.absolutePath}, size=${tempGciFile.length()}bytes")

                    val saveContext = SaveContext(
                        config = config!!,
                        romPath = game.localPath,
                        titleId = game.titleId,
                        emulatorPackage = emulatorPackage,
                        gameId = gameId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        emulatorId = resolvedEmulatorId
                    )
                    val result = gciSaveHandler.extractDownload(tempGciFile, saveContext)
                    if (!result.success) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | GCI extraction failed | error=${result.error}")
                        return@withContext SaveSyncResult.Error(result.error ?: "Failed to extract GCI save")
                    }
                    targetPath = result.targetPath!!
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | GCI extraction complete | target=$targetPath")
                } finally {
                    tempGciFile.delete()
                }
            } else {
                // Regular file saves (non-GCI)
                targetPath = preDownloadTargetPath ?: run {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine file save path")
                    return@withContext SaveSyncResult.Error("Cannot determine save path")
                }

                if (!hasEnoughDiskSpace(targetPath, contentLength)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient disk space for save file")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Response body is null for file save")
                    return@withContext SaveSyncResult.Error("Empty response body")
                }

                var tempSaveFile: File? = File(context.cacheDir, "temp_save_${System.currentTimeMillis()}.tmp")
                try {
                    body.byteStream().use { input ->
                        tempSaveFile!!.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Saved temp file | path=${tempSaveFile!!.absolutePath}, size=${tempSaveFile!!.length()}bytes")

                    val hasLocalHardcore = saveCacheManager.get().hasHardcoreSave(gameId)
                    val downloadedHasTrailer = saveArchiver.hasHardcoreTrailer(tempSaveFile!!)
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Hardcore check | localHardcore=$hasLocalHardcore, downloadedTrailer=$downloadedHasTrailer")
                    if (hasLocalHardcore && !downloadedHasTrailer) {
                        Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | NEEDS_HARDCORE_RESOLUTION | Server save missing trailer")
                        val tempPath = tempSaveFile!!.absolutePath
                        tempSaveFile = null
                        return@withContext SaveSyncResult.NeedsHardcoreResolution(
                            tempFilePath = tempPath,
                            gameId = gameId,
                            gameName = game.title,
                            emulatorId = resolvedEmulatorId,
                            targetPath = targetPath,
                            isFolderBased = false,
                            channelName = channelName ?: syncEntity.channelName
                        )
                    }

                    val existingTarget = File(targetPath)
                    if (existingTarget.exists() && !skipBackup) {
                        try {
                            saveCacheManager.get().cacheCurrentSave(gameId, resolvedEmulatorId, targetPath)
                            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached existing save before overwrite")
                        } catch (e: Exception) {
                            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed, aborting download to prevent data loss", e)
                            tempSaveFile?.delete()
                            return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                        }
                    }

                    val bytesWithoutTrailer = saveArchiver.readBytesWithoutTrailer(tempSaveFile!!)
                    val written = if (bytesWithoutTrailer != null) {
                        saveArchiver.writeBytesToPath(targetPath, bytesWithoutTrailer)
                    } else {
                        saveArchiver.copyFileToPath(tempSaveFile!!, targetPath)
                    }
                    if (!written) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Failed to write file save | path=$targetPath")
                        return@withContext SaveSyncResult.Error("Failed to write save file")
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | File save written | path=$targetPath")
                } finally {
                    tempSaveFile?.delete()
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

            if (isSwitchEmulator && game.titleId == null) {
                val extractedTitleId = File(targetPath).name
                if (isValidSwitchTitleId(extractedTitleId)) {
                    gameDao.updateTitleId(gameId, extractedTitleId)
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached titleId from save folder | titleId=$extractedTitleId")
                } else if (isValidSwitchHexId(extractedTitleId)) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Skipping invalid titleId=$extractedTitleId (doesn't start with 01)")
                }
            }

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
                    emulatorId = resolvedEmulatorId,
                    savePath = targetPath,
                    channelName = cacheChannelName,
                    isLocked = cacheIsLocked
                )
            } catch (e: Exception) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cache creation failed", e)
            }

            Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Complete | path=$targetPath, channel=$effectiveChannelName")

            SaveDebugLogger.logSyncDownloadCompleted(
                gameId = gameId,
                gameName = game.title,
                channel = effectiveChannelName,
                sizeBytes = File(targetPath).let { if (it.isDirectory) it.walkTopDown().sumOf { f -> f.length() } else it.length() },
                durationMs = System.currentTimeMillis() - downloadStartTime
            )

            SaveSyncResult.Success
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Exception during download", e)

            SaveDebugLogger.logSyncDownloadFailed(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                error = e.message ?: "Unknown exception"
            )

            SaveSyncResult.Error(e.message ?: "Download failed")
        } finally {
            tempZipFile?.delete()
        }
    }

    suspend fun downloadSaveById(
        serverSaveId: Long,
        targetPath: String,
        emulatorId: String,
        emulatorPackage: String? = null,
        gameId: Long? = null,
        romPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext false

        val serverSave = try {
            api.getSave(serverSaveId).body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveById: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isGciFormat = config?.usesGciFormat == true
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true) && !isGciFormat
        val isSwitchEmulator = emulatorId in SWITCH_EMULATOR_IDS

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath ?: return@withContext false

            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Logger.error(TAG, "downloadSaveById failed: ${response.code()}")
                return@withContext false
            }

            if (isGciFormat && romPath != null && gameId != null) {
                // GCI format: download to temp, detect bundle vs single, extract appropriately
                val tempGciFile = File(context.cacheDir, "temp_gci_${System.currentTimeMillis()}.tmp")
                try {
                    val body = response.body() ?: return@withContext false
                    body.byteStream().use { input ->
                        tempGciFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val isZipBundle = tempGciFile.inputStream().use { input ->
                        val magic = ByteArray(2)
                        input.read(magic) == 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
                    }

                    if (isZipBundle) {
                        tempZipFile = tempGciFile
                        val extractedPaths = extractGciBundle(tempGciFile, config, romPath, gameId)
                        if (extractedPaths.isEmpty()) {
                            Logger.error(TAG, "downloadSaveById: GCI bundle extraction failed")
                            return@withContext false
                        }
                        Logger.debug(TAG, "downloadSaveById: GCI bundle extracted | paths=${extractedPaths.size}")
                    } else {
                        // Single GCI file - use FileAccessLayer for proper scoped storage handling
                        val gciInfo = GameCubeHeaderParser.parseGciHeader(tempGciFile)
                        val romInfo = GameCubeHeaderParser.parseRomHeader(File(romPath))
                        if (gciInfo != null && romInfo != null) {
                            val gciFilename = GameCubeHeaderParser.buildGciFilename(
                                gciInfo.makerCode, gciInfo.gameId, gciInfo.internalFilename
                            )
                            val basePaths = SavePathRegistry.resolvePath(config, "ngc", null)
                            val baseDir = basePaths.firstOrNull { directoryExists(it) } ?: basePaths.firstOrNull()
                            if (baseDir != null) {
                                val resolvedPath = GameCubeHeaderParser.buildGciPath(baseDir, romInfo.region, gciFilename)
                                val parentDir = File(resolvedPath).parent
                                if (parentDir != null) fal.mkdirs(parentDir)
                                if (fal.copyFile(tempGciFile.absolutePath, resolvedPath)) {
                                    Logger.debug(TAG, "downloadSaveById: GCI single file written | path=$resolvedPath")
                                } else {
                                    Logger.error(TAG, "downloadSaveById: failed to copy GCI file | path=$resolvedPath")
                                }
                            }
                        }
                        tempGciFile.delete()
                    }
                } catch (e: Exception) {
                    tempGciFile.delete()
                    throw e
                }
                return@withContext true
            } else if (isFolderBased) {
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "downloadSaveById: response body is null for folder save")
                    return@withContext false
                }
                body.byteStream().use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val resolvedTargetPath = if (isSwitchEmulator && config != null) {
                    savePathResolver.resolveSwitchSaveTargetPath(tempZipFile, config, emulatorPackage) ?: targetPath
                } else {
                    targetPath
                }

                val targetFolder = File(resolvedTargetPath)
                targetFolder.mkdirs()

                val isJksv = saveArchiver.isJksvFormat(tempZipFile)
                val unzipSuccess = if (isJksv) {
                    saveArchiver.unzipPreservingStructure(tempZipFile, targetFolder, SwitchSaveHandler.JKSV_EXCLUDE_FILES)
                } else {
                    saveArchiver.unzipSingleFolder(tempZipFile, targetFolder)
                }
                if (!unzipSuccess) {
                    return@withContext false
                }
            } else {
                val targetFile = File(targetPath)
                targetFile.parentFile?.mkdirs()

                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "downloadSaveById: response body is null for file save")
                    return@withContext false
                }
                body.byteStream().use { input ->
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

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500,
        tag: String = "",
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    val delayMs = initialDelayMs * (1 shl attempt)
                    Logger.debug(TAG, "$tag retry ${attempt + 1}/$maxAttempts after ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: IOException("Retry failed")
    }

    private fun hasEnoughDiskSpace(targetPath: String, requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) return true
        return try {
            val parentDir = File(targetPath).parentFile ?: File(targetPath)
            val existingDir = generateSequence(parentDir) { it.parentFile }
                .firstOrNull { it.exists() } ?: return true
            val stat = StatFs(existingDir.absolutePath)
            val availableBytes = stat.availableBytes
            val hasSpace = availableBytes > requiredBytes * 2
            if (!hasSpace) {
                Logger.warn(TAG, "Insufficient disk space: available=${availableBytes}bytes, required=${requiredBytes * 2}bytes")
            }
            hasSpace
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to check disk space", e)
            true
        }
    }

    suspend fun queueUpload(gameId: Long, emulatorId: String, localPath: String) {
        val game = gameDao.getById(gameId) ?: return
        val rommId = game.rommId ?: return

        val payload = SaveFilePayload(emulatorId)
        pendingSyncQueueDao.deleteByGameAndType(gameId, SyncType.SAVE_FILE)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = payload.toJson()
            )
        )
    }

    suspend fun scanAndQueueLocalChanges(): Int = withContext(Dispatchers.IO) {
        val downloadedGames = gameDao.getGamesWithLocalPath().filter { it.rommId != null }
        var queued = 0

        for (game in downloadedGames) {
            val emulatorId = resolveEmulatorForGame(game) ?: continue
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
            val folderSyncEnabled = isFolderSaveSyncEnabled()

            val savePath = savePathResolver.discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                emulatorPackage = emulatorPackage,
                gameId = game.id,
                isFolderSaveSyncEnabled = folderSyncEnabled
            ) ?: continue

            val localFile = File(savePath)
            if (!localFile.exists()) continue

            val localModified = if (localFile.isDirectory) {
                Instant.ofEpochMilli(savePathResolver.findNewestFileTime(savePath))
            } else {
                Instant.ofEpochMilli(localFile.lastModified())
            }

            val syncEntity = saveSyncDao.getByGameAndEmulator(game.id, emulatorId)
            val lastSynced = syncEntity?.lastSyncedAt

            if (lastSynced == null || localModified.isAfter(lastSynced)) {
                Logger.debug(TAG, "[SaveSync] SCAN gameId=${game.id} | Local newer than sync | local=$localModified, lastSync=$lastSynced")
                queueUpload(game.id, emulatorId, savePath)
                queued++
            }
        }

        Logger.info(TAG, "[SaveSync] SCAN | Queued $queued local saves for upload")
        queued
    }

    suspend fun processPendingUploads(): Int = withContext(Dispatchers.IO) {
        val pending = pendingSyncQueueDao.getRetryableBySyncType(SyncType.SAVE_FILE)
        if (pending.isEmpty()) {
            return@withContext 0
        }
        Logger.info(TAG, "Processing ${pending.size} pending save uploads")

        for (item in pending) {
            val game = gameDao.getById(item.gameId) ?: continue
            syncQueueManager.addOperation(
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
            val payload = SaveFilePayload.fromJson(item.payloadJson) ?: continue
            Logger.debug(TAG, "Processing pending upload: gameId=${item.gameId}, emulator=${payload.emulatorId}")
            syncQueueManager.updateOperation(item.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = uploadSave(item.gameId, payload.emulatorId)) {
                is SaveSyncResult.Success -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    syncQueueManager.completeOperation(item.gameId)
                    processed++
                }
                is SaveSyncResult.Conflict -> {
                    Logger.debug(TAG, "Pending upload conflict for gameId=${item.gameId}, leaving in queue")
                    syncQueueManager.completeOperation(item.gameId, "Server has newer save")
                }
                is SaveSyncResult.Error -> {
                    Logger.debug(TAG, "Pending upload failed for gameId=${item.gameId}: ${result.message}")
                    pendingSyncQueueDao.markFailed(item.id, result.message)
                    syncQueueManager.completeOperation(item.gameId, result.message)
                }
                else -> {
                    syncQueueManager.completeOperation(item.gameId, "Sync not available")
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
            syncQueueManager.addOperation(
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
            syncQueueManager.updateOperation(syncEntity.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = downloadSave(syncEntity.gameId, syncEntity.emulatorId, syncEntity.channelName)) {
                is SaveSyncResult.Success -> {
                    syncQueueManager.completeOperation(syncEntity.gameId)
                    downloaded++
                }
                is SaveSyncResult.Error -> {
                    syncQueueManager.completeOperation(syncEntity.gameId, result.message)
                }
                else -> {
                    syncQueueManager.completeOperation(syncEntity.gameId, "Download failed")
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
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, DEFAULT_SAVE_NAME)
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
            val activeChannel = gameDao.getActiveSaveChannel(gameId)
            Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId rommId=$rommId emulator=$emulatorId channel=$activeChannel | Checking server saves")
            val api = this@SaveSyncRepository.api
            if (api == null) {
                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | No API connection")
                return@withContext PreLaunchSyncResult.NoConnection
            }

            // Skip sync if user explicitly chose to keep local save
            val activeSaveApplied = gameDao.getActiveSaveApplied(gameId)
            if (activeSaveApplied == true) {
                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Skipping - user chose to keep local (activeSaveApplied=true)")
                return@withContext PreLaunchSyncResult.LocalIsNewer
            }

            try {
                val serverSaves = checkSavesForGame(gameId, rommId)
                val matchingSaves = serverSaves.filter { it.emulator == emulatorId || it.emulator == null }
                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Found ${serverSaves.size} server saves | matching=$emulatorId: ${matchingSaves.size}, channels=${matchingSaves.map { it.fileNameNoExt }}")

                val game = gameDao.getById(gameId)
                val romBaseName = game?.localPath?.let { File(it).nameWithoutExtension }

                val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
                val serverSave = if (activeChannel != null) {
                    val channelSave = matchingSaves.find { it.fileNameNoExt == activeChannel }
                    if (channelSave != null) {
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Using active channel save | channel=$activeChannel")
                        channelSave
                    } else {
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Active channel '$activeChannel' not found on server, no fallback")
                        null
                    }
                } else {
                    // For GCI format, prefer ZIP bundles over single .gci files
                    val candidates = matchingSaves.filter { isLatestSaveFileName(it.fileName, romBaseName) }
                    if (config?.usesGciFormat == true && candidates.size > 1) {
                        val preferred = candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                            ?: candidates.firstOrNull()
                        if (preferred != null && preferred != candidates.firstOrNull()) {
                            Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | GCI format: preferring ZIP bundle over single file")
                        }
                        preferred
                    } else {
                        candidates.firstOrNull()
                    }
                }
                if (serverSave == null) {
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | No server save found for emulator=$emulatorId")
                    return@withContext PreLaunchSyncResult.NoServerSave
                }

                val serverTime = parseTimestamp(serverSave.updatedAt)
                val selectedChannel = serverSave.fileNameNoExt
                val existing = if (selectedChannel != null) {
                    saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, selectedChannel)
                        ?: saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, selectedChannel)
                } else {
                    saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
                }

                val validatedPath = existing?.localSavePath?.takeIf { path ->
                    if (game?.platformSlug == "switch") switchSaveHandler.isValidCachedSavePath(path) else true
                }
                if (existing?.localSavePath != null && validatedPath == null) {
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Rejecting invalid cached Switch path=${existing.localSavePath}")
                }
                val localFile = validatedPath?.let { File(it) }?.takeIf { it.exists() }
                val localFileTime = localFile?.let { Instant.ofEpochMilli(it.lastModified()) }
                val localDbTime = if (validatedPath != null) existing?.localUpdatedAt else null
                // Use the MORE RECENT of DB time and filesystem time to catch manual modifications
                val localTime = when {
                    localDbTime == null -> localFileTime
                    localFileTime == null -> localDbTime
                    localFileTime.isAfter(localDbTime) -> localFileTime
                    else -> localDbTime
                }

                val deltaMs = if (localTime != null) serverTime.toEpochMilli() - localTime.toEpochMilli() else null
                val deltaStr = deltaMs?.let { if (it >= 0) "+${it/1000}s" else "${it/1000}s" } ?: "N/A"
                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Timestamp compare | server=$serverTime, local=$localTime (db=$localDbTime, file=$localFileTime), delta=$deltaStr")

                // Safety check: Compare local save hash against cached save hash to detect unsaved changes
                // This catches cases where session end didn't trigger (external emulators) and local has changed
                // Run BEFORE timestamp comparison - filesystem state takes priority
                if (localFile != null && validatedPath != null) {
                    val activeSaveTimestamp = gameDao.getActiveSaveTimestamp(gameId)
                    val cachedSave = if (activeSaveTimestamp != null) {
                        saveCacheManager.get().getByTimestamp(gameId, activeSaveTimestamp)
                    } else {
                        selectedChannel?.let { saveCacheManager.get().getMostRecentInChannel(gameId, it) }
                    }
                    val cachedHash = cachedSave?.contentHash
                    if (cachedHash != null) {
                        val localHash = saveCacheManager.get().calculateLocalSaveHash(validatedPath)
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Hash compare | local=$localHash, cached=$cachedHash")
                        if (localHash != null && localHash != cachedHash) {
                            Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_MODIFIED | Local save differs from cached, prompting user")
                            return@withContext PreLaunchSyncResult.LocalModified(
                                localSavePath = validatedPath,
                                serverTimestamp = serverTime,
                                channelName = selectedChannel
                            )
                        }
                    } else {
                        // No specific cached hash to compare - check if local differs from ANY known cache
                        val localHash = saveCacheManager.get().calculateLocalSaveHash(validatedPath)
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | No cached hash to compare, checking against all caches (localHash=$localHash)")
                        if (localHash != null) {
                            val matchingCache = saveCacheManager.get().getByGameAndHash(gameId, localHash)
                            if (matchingCache == null) {
                                // Local file has unknown hash - treat as modified
                                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_MODIFIED | Local hash not found in any cache, prompting user")
                                return@withContext PreLaunchSyncResult.LocalModified(
                                    localSavePath = validatedPath,
                                    serverTimestamp = serverTime,
                                    channelName = selectedChannel
                                )
                            } else {
                                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Local hash matches cache id=${matchingCache.id}")
                            }
                        }
                    }
                }

                if (localFile != null && localTime != null && !serverTime.isAfter(localTime)) {
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_NEWER | Local is newer or equal, skipping download")
                    return@withContext PreLaunchSyncResult.LocalIsNewer
                }

                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=SERVER_NEWER | Will download channel=$selectedChannel")
                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = existing?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        channelName = selectedChannel,
                        rommSaveId = serverSave.id,
                        localSavePath = existing?.localSavePath,
                        localUpdatedAt = existing?.localUpdatedAt,
                        serverUpdatedAt = serverTime,
                        lastSyncedAt = existing?.lastSyncedAt,
                        syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
                    )
                )

                PreLaunchSyncResult.ServerIsNewer(serverTime, selectedChannel)
            } catch (e: Exception) {
                Logger.warn(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Error checking server saves", e)
                PreLaunchSyncResult.NoConnection
            }
        }

    sealed class PreLaunchSyncResult {
        data object NoConnection : PreLaunchSyncResult()
        data object NoServerSave : PreLaunchSyncResult()
        data object LocalIsNewer : PreLaunchSyncResult()
        data class ServerIsNewer(val serverTimestamp: Instant, val channelName: String?) : PreLaunchSyncResult()
        data class LocalModified(
            val localSavePath: String,
            val serverTimestamp: Instant,
            val channelName: String?
        ) : PreLaunchSyncResult()
    }

    private suspend fun findLocalSavePath(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): String? {
        val game = gameDao.getById(gameId) ?: return null
        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            resolveEmulatorForGame(game) ?: return null
        } else emulatorId

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, resolvedEmulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, resolvedEmulatorId, DEFAULT_SAVE_NAME)
        }

        val cachedPath = syncEntity?.localSavePath?.takeIf { path ->
            if (game.platformSlug == "switch") switchSaveHandler.isValidCachedSavePath(path) else true
        }
        if (cachedPath != null) return cachedPath

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val folderSyncEnabled = isFolderSaveSyncEnabled()

        return savePathResolver.discoverSavePath(
            emulatorId = resolvedEmulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedTitleId = game.titleId,
            emulatorPackage = emulatorPackage,
            gameId = gameId,
            isFolderSaveSyncEnabled = folderSyncEnabled
        )
    }

    suspend fun checkForConflict(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): ConflictInfo? = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext null

        val game = gameDao.getById(gameId) ?: return@withContext null
        val rommId = game.rommId ?: return@withContext null

        val localPath = findLocalSavePath(gameId, emulatorId, channelName)
        if (localPath == null) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | No local save path found")
            return@withContext null
        }
        val localFile = File(localPath)
        if (!localFile.exists()) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | Local file does not exist")
            return@withContext null
        }
        val localModified = Instant.ofEpochMilli(localFile.lastModified())

        val serverSaves = try {
            checkSavesForGame(gameId, rommId)
        } catch (e: Exception) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | Failed to check server saves: ${e.message}")
            return@withContext null
        }

        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
        val matchingSaves = serverSaves.filter { it.emulator == emulatorId || it.emulator == null }
        val serverSave = if (channelName != null) {
            matchingSaves.find { it.fileNameNoExt == channelName }
        } else {
            matchingSaves.filter { isLatestSaveFileName(it.fileName, romBaseName) }.firstOrNull()
        }

        if (serverSave == null) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | No matching server save")
            return@withContext null
        }

        val serverTime = parseTimestamp(serverSave.updatedAt)

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, DEFAULT_SAVE_NAME)
        }

        val isHashConflict = if (syncEntity?.lastUploadedHash != null) {
            val localHash = saveCacheManager.get().calculateLocalSaveHash(localPath)
            localHash != null && localHash != syncEntity.lastUploadedHash
        } else false

        val isTimestampConflict = serverTime.isAfter(localModified)

        if (isTimestampConflict || isHashConflict) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | Conflict detected: timestamp=$isTimestampConflict, hash=$isHashConflict")
            ConflictInfo(
                gameId = gameId,
                gameName = game.title,
                channelName = channelName,
                localTimestamp = localModified,
                serverTimestamp = serverTime,
                isHashConflict = isHashConflict
            )
        } else {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | No conflict")
            null
        }
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

    suspend fun downloadSaveAsChannel(
        gameId: Long,
        serverSaveId: Long,
        channelName: String,
        emulatorId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val api = this@SaveSyncRepository.api ?: return@withContext false

        val serverSave = try {
            api.getSave(serverSaveId).body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveAsChannel: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val downloadPath = serverSave.downloadPath ?: return@withContext false

        val response = try {
            api.downloadRaw(downloadPath)
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveAsChannel: download failed", e)
            return@withContext false
        }

        if (!response.isSuccessful) {
            Logger.error(TAG, "downloadSaveAsChannel: download failed with ${response.code()}")
            return@withContext false
        }

        val tempFile = File(context.cacheDir, "save_channel_${System.currentTimeMillis()}.tmp")
        try {
            response.body()?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext false

            if (tempFile.length() == 0L) {
                Logger.error(TAG, "downloadSaveAsChannel: empty save data")
                return@withContext false
            }

            val cacheResult = saveCacheManager.get().cacheCurrentSave(
                gameId = gameId,
                emulatorId = emulatorId ?: "unknown",
                savePath = tempFile.absolutePath,
                channelName = channelName,
                isLocked = true
            )

            Logger.debug(TAG, "downloadSaveAsChannel: result=$cacheResult, channel=$channelName")
            cacheResult.success
        } finally {
            tempFile.delete()
        }
    }

    enum class HardcoreResolutionChoice {
        KEEP_HARDCORE,
        DOWNGRADE_TO_CASUAL,
        KEEP_LOCAL
    }

    suspend fun resolveHardcoreConflict(
        resolution: SaveSyncResult.NeedsHardcoreResolution,
        choice: HardcoreResolutionChoice
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        val tempFile = File(resolution.tempFilePath)

        try {
            when (choice) {
                HardcoreResolutionChoice.KEEP_HARDCORE -> {
                    Logger.info(TAG, "[SaveSync] RESOLVE gameId=${resolution.gameId} | KEEP_HARDCORE | Uploading local save")
                    tempFile.delete()
                    uploadSave(
                        gameId = resolution.gameId,
                        emulatorId = resolution.emulatorId,
                        channelName = resolution.channelName,
                        forceOverwrite = true,
                        isHardcore = true
                    )
                }

                HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL -> {
                    Logger.info(TAG, "[SaveSync] RESOLVE gameId=${resolution.gameId} | DOWNGRADE_TO_CASUAL | Applying server save")

                    val targetFile = File(resolution.targetPath)
                    if (resolution.isFolderBased) {
                        val unzipSuccess = saveArchiver.unzipSingleFolder(tempFile, targetFile)
                        if (!unzipSuccess) {
                            return@withContext SaveSyncResult.Error("Failed to unzip save")
                        }
                    } else {
                        val bytesWithoutTrailer = saveArchiver.readBytesWithoutTrailer(tempFile)
                        val written = if (bytesWithoutTrailer != null) {
                            saveArchiver.writeBytesToPath(resolution.targetPath, bytesWithoutTrailer)
                        } else {
                            saveArchiver.copyFileToPath(tempFile, resolution.targetPath)
                        }
                        if (!written) {
                            return@withContext SaveSyncResult.Error("Failed to write save file")
                        }
                    }

                    saveCacheManager.get().cacheCurrentSave(
                        gameId = resolution.gameId,
                        emulatorId = resolution.emulatorId,
                        savePath = resolution.targetPath,
                        channelName = resolution.channelName,
                        isHardcore = false
                    )

                    val syncEntity = saveSyncDao.getByGameAndEmulator(resolution.gameId, resolution.emulatorId)
                    if (syncEntity != null) {
                        saveSyncDao.upsert(
                            syncEntity.copy(
                                localSavePath = resolution.targetPath,
                                localUpdatedAt = Instant.now(),
                                lastSyncedAt = Instant.now(),
                                syncStatus = SaveSyncEntity.STATUS_SYNCED
                            )
                        )
                    }

                    SaveSyncResult.Success
                }

                HardcoreResolutionChoice.KEEP_LOCAL -> {
                    Logger.info(TAG, "[SaveSync] RESOLVE gameId=${resolution.gameId} | KEEP_LOCAL | Skipping sync")
                    SaveSyncResult.Success
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    companion object {
        private val ROMM_TIMESTAMP_TAG = Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2}(-\d+)?\]$""")
        private val SWITCH_EMULATOR_IDS = setOf(
            "yuzu", "ryujinx", "citron", "strato", "eden", "sudachi", "skyline"
        )
    }
}
